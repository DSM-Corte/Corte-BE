package com.example.cortebe.domain.review;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.judge.runner.Verdict;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.review.entity.LineComment;
import com.example.cortebe.domain.review.entity.ReviewComment;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.entity.ReviewRequest;
import com.example.cortebe.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class ReviewFixtures {

    public static final String CODE = "int a = 1;\nint b = 2;\nint c = 3;\n";

    private ReviewFixtures() {
    }

    public static User user(Long id) {
        User user = User.builder().email("user" + id + "@corte.com").password("password").nickname("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Problem problem(Long id) {
        Problem problem = Problem.builder().title("문제" + id).description("설명").category("구현")
            .memoryLimitMb(256).timeLimitMs(1000).build();
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    public static JudgeJob submission(Long id, User author, Problem problem, Verdict verdict) {
        JudgeJob job = JudgeJob.builder().problem(problem).author(author).submission(true).language(Language.JAVA)
            .code(CODE).snapshot("{}").tokenHash("x".repeat(64)).totalCount(1).build();
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "verdict", verdict);
        return job;
    }

    public static ReviewRequest request(Long id, User requester, boolean closed) {
        ReviewRequest request = ReviewRequest.builder().requester(requester).problem(problem(1L))
            .language(Language.JAVA).code(CODE).build();
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "closed", closed);
        return request;
    }

    public static LineComment lineComment(Long id, ReviewRequest request, User author, int lineNumber, String suggestedCode) {
        LineComment comment = LineComment.builder().reviewRequest(request).author(author).lineNumber(lineNumber)
            .content("코멘트").suggestedCode(suggestedCode).build();
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    public static ReviewComment comment(Long id, ReviewRequest request, User author) {
        ReviewComment comment = ReviewComment.builder().reviewRequest(request).author(author).content("댓글").build();
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    public static ReviewReply reply(Long id, ReviewRequest request, LineComment lineComment, ReviewComment comment,
                                    User author) {
        ReviewReply reply = ReviewReply.builder().reviewRequest(request).lineComment(lineComment)
            .reviewComment(comment).author(author).content("답글").build();
        ReflectionTestUtils.setField(reply, "id", id);
        return reply;
    }
}
