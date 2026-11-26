package com.example.cortebe.domain.board.repository;

import com.example.cortebe.domain.board.entity.Post;
import com.example.cortebe.domain.board.entity.PostComment;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PostCommentRepositoryTests {

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 목록_조회는_해당_게시글의_댓글만_작성자_닉네임과_함께_오래된_순으로_가져온다() {
        // given
        User author = userRepository.save(User.builder().email("a@corte.com").password("password").nickname("작성자").build());
        Post post = postRepository.save(Post.builder().title("제목").content("본문").author(author).build());
        Post otherPost = postRepository.save(Post.builder().title("다른 글").content("본문").author(author).build());
        postCommentRepository.save(PostComment.builder().post(post).author(author).content("첫 댓글").build());
        postCommentRepository.save(PostComment.builder().post(post).author(author).content("둘째 댓글").build());
        postCommentRepository.save(PostComment.builder().post(otherPost).author(author).content("남의 글 댓글").build());

        // when
        Page<PostCommentRepository.Summary> page = postCommentRepository.findSummaries(post.getId(),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id")));

        // then
        assertThat(page.getContent()).extracting(PostCommentRepository.Summary::getContent)
            .containsExactly("첫 댓글", "둘째 댓글");
        assertThat(page.getContent().getFirst().getAuthorNickname()).isEqualTo("작성자");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 게시글의_댓글만_모두_삭제한다() {
        // given
        User author = userRepository.save(User.builder().email("a@corte.com").password("password").nickname("작성자").build());
        Post post = postRepository.save(Post.builder().title("제목").content("본문").author(author).build());
        Post otherPost = postRepository.save(Post.builder().title("다른 글").content("본문").author(author).build());
        postCommentRepository.save(PostComment.builder().post(post).author(author).content("지울 댓글").build());
        postCommentRepository.save(PostComment.builder().post(otherPost).author(author).content("남길 댓글").build());

        // when
        postCommentRepository.deleteAllByPostId(post.getId());

        // then
        assertThat(postCommentRepository.findAll()).extracting(PostComment::getContent).containsExactly("남길 댓글");
    }
}
