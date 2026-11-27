package com.example.cortebe.domain.board.controller;

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
class PostControllerTests {

    private static final String CREATE_REQUEST = """
        {
            "title": "백준 알고리즘 문제 접근 방법 질문",
            "content": "## 문제 접근 방법\\n\\n이 문제를 BFS로 풀어도 될까요?\\n\\n```swift\\nlet graph = [[1, 2], [0, 3]]\\n```"
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private Long authorId;
    private String authorToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(User.builder().email("a@corte.com").password("password").nickname("사용자1").build());
        authorId = author.getId();
        authorToken = bearer(author);
        otherToken = bearer(userRepository.save(User.builder().email("b@corte.com").password("password").nickname("사용자2").build()));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    @Test
    void createGetPatchListDeleteLifecycle() throws Exception {
        String location = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.author").value("사용자1"))
            .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("```swift\nlet graph")))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("백준 알고리즘 문제 접근 방법 질문"));

        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"수정한 제목\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정한 제목"))
            .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.startsWith("## 문제 접근 방법")));

        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, otherToken)
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("수정한 제목"))
            .andExpect(jsonPath("$.content[0].author").value("사용자1"))
            .andExpect(jsonPath("$.content[0].content").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("B001"));
    }

    @Test
    void nonAuthorCannotUpdateOrDelete() throws Exception {
        String location = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"탈취\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("B002"));
        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(jsonPath("$.title").value("백준 알고리즘 문제 접근 방법 질문"));
    }

    @Test
    void createRejectsBlankTitleAndMissingToken() throws Exception {
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \" \", \"content\": \"본문\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 게시글_작성_상세_수정_응답에_작성자_ID를_주고_목록에는_주지_않는다() throws Exception {
        String location = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorId").value(authorId))
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorId").value(authorId))
            .andExpect(jsonPath("$.author").value("사용자1"));

        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"수정한 제목\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorId").value(authorId));

        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].authorId").doesNotExist());
    }
}
