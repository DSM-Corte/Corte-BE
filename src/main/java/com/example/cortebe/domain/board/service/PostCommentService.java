package com.example.cortebe.domain.board.service;

import com.example.cortebe.domain.board.dto.CreatePostCommentRequest;
import com.example.cortebe.domain.board.dto.PostCommentPageResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostCommentResponse create(Long userId, Long postId, CreatePostCommentRequest request) {
        Post post = postRepository.findById(postId)
            .orElseThrow(PostNotFoundException::new);
        User author = userRepository.findById(userId)
            .orElseThrow(UnauthorizedException::new);
        PostComment comment = postCommentRepository.save(PostComment.builder()
            .post(post)
            .author(author)
            .content(request.content())
            .build());
        return PostCommentResponse.from(comment);
    }

    public PostCommentPageResponse list(Long postId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException();
        }
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException();
        }
        // 대화 흐름대로 읽도록 오래된 댓글부터 보여준다
        return PostCommentPageResponse.from(postCommentRepository.findSummaries(postId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))));
    }

    @Transactional
    public PostCommentResponse update(Long userId, Long postId, Long commentId, UpdatePostCommentRequest request) {
        PostComment comment = findOwnedComment(userId, postId, commentId);
        comment.update(request.content());
        // 변경 감지의 flush 이후 @LastModifiedDate가 반영되도록 즉시 반영한다
        postCommentRepository.flush();
        return PostCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long userId, Long postId, Long commentId) {
        postCommentRepository.delete(findOwnedComment(userId, postId, commentId));
    }

    private PostComment findComment(Long postId, Long commentId) {
        PostComment comment = postCommentRepository.findWithAuthorById(commentId)
            .orElseThrow(PostCommentNotFoundException::new);
        // 다른 게시글 경로로는 댓글을 건드릴 수 없게 한다
        if (!comment.belongsTo(postId)) {
            throw new PostCommentNotFoundException();
        }
        return comment;
    }

    private PostComment findOwnedComment(Long userId, Long postId, Long commentId) {
        PostComment comment = findComment(postId, commentId);
        if (!comment.isWrittenBy(userId)) {
            throw new PostCommentForbiddenException();
        }
        return comment;
    }
}
