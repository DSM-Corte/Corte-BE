package com.example.cortebe.domain.board.controller;

import com.example.cortebe.domain.board.entity.Post;
import com.example.cortebe.domain.board.repository.PostCommentRepository;
import com.example.cortebe.domain.board.repository.PostRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostCommentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User author;
    private String authorToken;
    private String otherToken;
    private Long postId;
    private String commentsUrl;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder().email("a@corte.com").password("password").nickname("사용자1").build());
        User other = userRepository.save(User.builder().email("b@corte.com").password("password").nickname("사용자2").build());
        authorToken = bearer(author);
        otherToken = bearer(other);
        postId = postRepository.save(Post.builder().title("제목").content("본문").author(author).build()).getId();
        commentsUrl = "/api/posts/" + postId + "/comments";
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private String createComment(String token, String content) throws Exception {
        return mockMvc.perform(post(commentsUrl).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"" + content + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void 댓글을_작성하면_201과_Location과_댓글을_준다() throws Exception {
        mockMvc.perform(post(commentsUrl).header(HttpHeaders.AUTHORIZATION, otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"좋은 글이네요\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(commentsUrl + "/")))
            .andExpect(jsonPath("$.content").value("좋은 글이네요"))
            .andExpect(jsonPath("$.author").value("사용자2"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void 댓글_목록은_오래된_순으로_페이징해서_준다() throws Exception {
        createComment(authorToken, "첫 댓글");
        createComment(otherToken, "둘째 댓글");
        createComment(authorToken, "셋째 댓글");

        mockMvc.perform(get(commentsUrl).header(HttpHeaders.AUTHORIZATION, otherToken)
                .param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].content").value("첫 댓글"))
            .andExpect(jsonPath("$.content[0].author").value("사용자1"))
            .andExpect(jsonPath("$.content[1].content").value("둘째 댓글"))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void 작성자는_댓글을_수정할_수_있다() throws Exception {
        String location = createComment(authorToken, "원래 댓글");

        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"수정한 댓글\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("수정한 댓글"));
    }

    @Test
    void 작성자는_댓글을_삭제할_수_있다() throws Exception {
        String location = createComment(authorToken, "지울 댓글");

        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(commentsUrl).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 작성자가_아니면_수정과_삭제에_403_B004를_준다() throws Exception {
        String location = createComment(authorToken, "원래 댓글");

        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"탈취\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("B004"));
        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("B004"));
    }

    @Test
    void 없는_게시글이면_404_B001을_준다() throws Exception {
        mockMvc.perform(post("/api/posts/999999/comments").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"댓글\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("B001"));
        mockMvc.perform(get("/api/posts/999999/comments").header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("B001"));
    }

    @Test
    void 없는_댓글이거나_다른_게시글의_댓글이면_404_B003을_준다() throws Exception {
        String location = createComment(authorToken, "댓글");
        Long otherPostId = postRepository.save(Post.builder().title("다른 글").content("본문").author(author).build()).getId();
        String commentId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(patch(commentsUrl + "/999999").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"수정\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("B003"));
        mockMvc.perform(delete("/api/posts/" + otherPostId + "/comments/" + commentId)
                .header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("B003"));
    }

    @Test
    void 본문이_공백이거나_500자를_넘으면_400_C001을_준다() throws Exception {
        String location = createComment(authorToken, "댓글");

        mockMvc.perform(post(commentsUrl).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(post(commentsUrl).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"" + "가".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 본문이_정확히_500자면_작성할_수_있다() throws Exception {
        mockMvc.perform(post(commentsUrl).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"" + "가".repeat(500) + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void 페이지_범위가_잘못되면_400_C001을_준다() throws Exception {
        mockMvc.perform(get(commentsUrl).header(HttpHeaders.AUTHORIZATION, authorToken).param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 토큰이_없으면_401_A001을_준다() throws Exception {
        mockMvc.perform(get(commentsUrl))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 게시글을_삭제하면_댓글도_함께_삭제된다() throws Exception {
        createComment(authorToken, "첫 댓글");
        createComment(otherToken, "둘째 댓글");
        assertThat(postCommentRepository.count()).isEqualTo(2);

        mockMvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNoContent());

        assertThat(postCommentRepository.count()).isZero();
    }
}
