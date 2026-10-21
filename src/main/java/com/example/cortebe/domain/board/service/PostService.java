package com.example.cortebe.domain.board.service;

import com.example.cortebe.domain.board.dto.CreatePostRequest;
import com.example.cortebe.domain.board.dto.PostPageResponse;
import com.example.cortebe.domain.board.dto.PostResponse;
import com.example.cortebe.domain.board.dto.UpdatePostRequest;
import com.example.cortebe.domain.board.entity.Post;
import com.example.cortebe.domain.board.exception.PostForbiddenException;
import com.example.cortebe.domain.board.exception.PostNotFoundException;
import com.example.cortebe.domain.board.repository.PostRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostResponse create(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId)
            .orElseThrow(() -> new CorteException(AuthErrorCode.UNAUTHORIZED));
        Post post = postRepository.save(new Post(request.title(), request.content(), author));
        return toResponse(post);
    }

    public PostResponse get(Long id) {
        return toResponse(findPost(id));
    }

    public PostPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        }
        Page<PostRepository.Summary> posts = postRepository.findSummaries(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PostPageResponse(posts.getContent().stream()
            .map(post -> new PostPageResponse.Summary(post.getId(), post.getTitle(),
                post.getAuthorNickname(), post.getCreatedAt())).toList(),
            posts.getNumber(), posts.getSize(), posts.getTotalElements(), posts.getTotalPages());
    }

    @Transactional
    public PostResponse update(Long userId, Long id, UpdatePostRequest request) {
        Post post = findOwnedPost(userId, id);
        post.update(request.title(), request.content());
        // 변경 감지의 flush 이후 @UpdateTimestamp가 반영되도록 즉시 반영한다
        postRepository.flush();
        return toResponse(post);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        postRepository.delete(findOwnedPost(userId, id));
    }

    private Post findPost(Long id) {
        return postRepository.findWithAuthorById(id)
            .orElseThrow(() -> new PostNotFoundException());
    }

    private Post findOwnedPost(Long userId, Long id) {
        Post post = findPost(id);
        if (!post.isWrittenBy(userId)) {
            throw new PostForbiddenException();
        }
        return post;
    }

    private PostResponse toResponse(Post post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
            post.getAuthor().getNickname(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
