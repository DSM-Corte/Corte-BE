package com.example.cortebe.domain.board.service;

import com.example.cortebe.domain.board.dto.CreatePostCommentRequest;
import com.example.cortebe.domain.board.dto.PostCommentResponse;
import com.example.cortebe.domain.board.dto.UpdatePostCommentRequest;
import com.example.cortebe.domain.board.entity.Post;
import com.example.cortebe.domain.board.entity.PostComment;
import com.example.cortebe.domain.board.exception.PostCommentForbiddenException;
import com.example.cortebe.domain.board.exception.PostCommentNotFoundException;
import com.example.cortebe.domain.board.exception.PostNotFoundException;
import com.example.cortebe.domain.board.repository.PostCommentRepository;
import com.example.cortebe.domain.board.repository.PostRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTests {

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostCommentService postCommentService;

    private User userWithId(Long id, String nickname) {
        User user = User.builder().email(nickname + "@corte.com").password("password").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post postWithId(Long id) {
        Post post = Post.builder().title("제목").content("본문").author(userWithId(9L, "글쓴이")).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private PostComment commentOf(Post post, User author, String content) {
        return PostComment.builder().post(post).author(author).content(content).build();
    }

    @Test
    void 댓글을_작성하면_작성자_닉네임과_함께_응답한다() {
        // given
        given(postRepository.findById(10L)).willReturn(Optional.of(postWithId(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, "사용자1")));
        given(postCommentRepository.save(any(PostComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PostCommentResponse response = postCommentService.create(1L, 10L, new CreatePostCommentRequest("좋은 글이네요"));

        // then
        assertThat(response.content()).isEqualTo("좋은 글이네요");
        assertThat(response.author()).isEqualTo("사용자1");
    }

    @Test
    void 게시글이_없으면_댓글을_작성할_수_없다() {
        // given
        given(postRepository.findById(10L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postCommentService.create(1L, 10L, new CreatePostCommentRequest("댓글")))
            .isInstanceOf(PostNotFoundException.class);
        then(postCommentRepository).should(never()).save(any());
    }

    @Test
    void 회원이_없으면_댓글을_작성할_수_없다() {
        // given
        given(postRepository.findById(10L)).willReturn(Optional.of(postWithId(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postCommentService.create(1L, 10L, new CreatePostCommentRequest("댓글")))
            .isInstanceOf(UnauthorizedException.class);
        then(postCommentRepository).should(never()).save(any());
    }

    @Test
    void 댓글_목록은_오래된_순으로_조회해_응답으로_바꾼다() {
        // given
        PostCommentRepository.Summary summary = new PostCommentRepository.Summary() {
            public Long getId() { return 5L; }
            public String getContent() { return "댓글"; }
            public String getAuthorNickname() { return "사용자1"; }
            public Long getAuthorId() { return 1L; }
            public LocalDateTime getCreatedAt() { return LocalDateTime.of(2026, 11, 25, 10, 0); }
            public LocalDateTime getUpdatedAt() { return LocalDateTime.of(2026, 11, 25, 10, 0); }
        };
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        given(postRepository.existsById(10L)).willReturn(true);
        given(postCommentRepository.findSummaries(10L, pageable))
            .willReturn(new PageImpl<>(List.of(summary), pageable, 1));

        // when
        var response = postCommentService.list(10L, 0, 20);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().author()).isEqualTo("사용자1");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void 게시글이_없으면_댓글_목록을_조회할_수_없다() {
        // given
        given(postRepository.existsById(10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> postCommentService.list(10L, 0, 20))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void 페이지_범위가_잘못되면_댓글_목록_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> postCommentService.list(10L, -1, 20)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> postCommentService.list(10L, 0, 0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> postCommentService.list(10L, 0, 101)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 작성자가_수정하면_본문이_바뀐다() {
        // given
        PostComment comment = commentOf(postWithId(10L), userWithId(1L, "사용자1"), "원래 댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when
        PostCommentResponse response = postCommentService.update(1L, 10L, 5L, new UpdatePostCommentRequest("수정한 댓글"));

        // then
        assertThat(response.content()).isEqualTo("수정한 댓글");
    }

    @Test
    void 수정_본문이_null이면_기존_본문을_유지한다() {
        // given
        PostComment comment = commentOf(postWithId(10L), userWithId(1L, "사용자1"), "원래 댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when
        PostCommentResponse response = postCommentService.update(1L, 10L, 5L, new UpdatePostCommentRequest(null));

        // then
        assertThat(response.content()).isEqualTo("원래 댓글");
    }

    @Test
    void 작성자가_아니면_댓글을_수정할_수_없다() {
        // given
        PostComment comment = commentOf(postWithId(10L), userWithId(1L, "사용자1"), "원래 댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> postCommentService.update(2L, 10L, 5L, new UpdatePostCommentRequest("탈취")))
            .isInstanceOf(PostCommentForbiddenException.class);
        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    void 댓글이_없으면_수정할_수_없다() {
        // given
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postCommentService.update(1L, 10L, 5L, new UpdatePostCommentRequest("수정")))
            .isInstanceOf(PostCommentNotFoundException.class);
    }

    @Test
    void 다른_게시글의_댓글이면_수정할_수_없다() {
        // given
        PostComment comment = commentOf(postWithId(11L), userWithId(1L, "사용자1"), "원래 댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> postCommentService.update(1L, 10L, 5L, new UpdatePostCommentRequest("수정")))
            .isInstanceOf(PostCommentNotFoundException.class);
        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    void 작성자가_삭제하면_댓글이_지워진다() {
        // given
        PostComment comment = commentOf(postWithId(10L), userWithId(1L, "사용자1"), "댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when
        postCommentService.delete(1L, 10L, 5L);

        // then
        then(postCommentRepository).should().delete(comment);
    }

    @Test
    void 작성자가_아니면_댓글을_삭제할_수_없다() {
        // given
        PostComment comment = commentOf(postWithId(10L), userWithId(1L, "사용자1"), "댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> postCommentService.delete(2L, 10L, 5L))
            .isInstanceOf(PostCommentForbiddenException.class);
        then(postCommentRepository).should(never()).delete(any());
    }

    @Test
    void 다른_게시글의_댓글이면_삭제할_수_없다() {
        // given
        PostComment comment = commentOf(postWithId(11L), userWithId(1L, "사용자1"), "댓글");
        given(postCommentRepository.findWithAuthorById(5L)).willReturn(Optional.of(comment));

        // when & then
        assertThatThrownBy(() -> postCommentService.delete(1L, 10L, 5L))
            .isInstanceOf(PostCommentNotFoundException.class);
        then(postCommentRepository).should(never()).delete(any());
    }
}
