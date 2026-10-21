package com.example.cortebe.domain.board.service;

import com.example.cortebe.domain.board.dto.CreatePostRequest;
import com.example.cortebe.domain.board.dto.UpdatePostRequest;
import com.example.cortebe.domain.board.entity.Post;
import com.example.cortebe.domain.board.exception.PostForbiddenException;
import com.example.cortebe.domain.board.exception.PostNotFoundException;
import com.example.cortebe.domain.board.repository.PostRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTests {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    private User userWithId(Long id, String nickname) {
        User user = User.builder().email(nickname + "@corte.com").password("password").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void createPreservesMarkdownContentAsIs() {
        String markdown = "## 접근\n\n```swift\nlet a = 1\n```\n  끝  \n";
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithId(1L, "사용자1")));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = postService.create(1L, new CreatePostRequest("제목", markdown));

        assertThat(response.content()).isEqualTo(markdown);
        assertThat(response.author()).isEqualTo("사용자1");
    }

    @Test
    void createFailsWhenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(1L, new CreatePostRequest("제목", "본문")))
            .isInstanceOf(CorteException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void getFailsWhenPostDoesNotExist() {
        when(postRepository.findWithAuthorById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.get(1L))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void listRejectsInvalidPaging() {
        assertThatThrownBy(() -> postService.list(-1, 20))
            .isInstanceOfSatisfying(CorteException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> postService.list(0, 101))
            .isInstanceOf(CorteException.class);
    }

    @Test
    void updateByAuthorKeepsOmittedFields() {
        Post post = new Post("원래 제목", "원래 본문", userWithId(1L, "사용자1"));
        when(postRepository.findWithAuthorById(10L)).thenReturn(Optional.of(post));

        var response = postService.update(1L, 10L, new UpdatePostRequest("새 제목", null));

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.content()).isEqualTo("원래 본문");
    }

    @Test
    void updateByNonAuthorIsForbidden() {
        Post post = new Post("제목", "본문", userWithId(1L, "사용자1"));
        when(postRepository.findWithAuthorById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(2L, 10L, new UpdatePostRequest("해킹", null)))
            .isInstanceOf(PostForbiddenException.class);
        assertThat(post.getTitle()).isEqualTo("제목");
    }

    @Test
    void deleteByNonAuthorIsForbidden() {
        Post post = new Post("제목", "본문", userWithId(1L, "사용자1"));
        when(postRepository.findWithAuthorById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(2L, 10L))
            .isInstanceOf(PostForbiddenException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void deleteByAuthorDeletesPost() {
        Post post = new Post("제목", "본문", userWithId(1L, "사용자1"));
        when(postRepository.findWithAuthorById(10L)).thenReturn(Optional.of(post));

        postService.delete(1L, 10L);

        verify(postRepository).delete(post);
    }
}
