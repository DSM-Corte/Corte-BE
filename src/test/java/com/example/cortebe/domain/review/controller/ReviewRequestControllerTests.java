package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.judge.entity.JudgeJob;
import com.example.cortebe.domain.judge.runner.Verdict;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewRequestControllerTests extends ReviewControllerTestSupport {

    @Test
    void 정답_제출로_리뷰를_요청하면_201과_위치를_반환한다() throws Exception {
        // given
        JudgeJob submission = saveSubmission(requester, Verdict.ACCEPTED);

        // when & then
        json(post("/api/review-requests"), requesterToken, "{\"submissionId\": " + submission.getId() + "}")
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.code").value(CODE))
            .andExpect(jsonPath("$.problemId").value(problem.getId()))
            .andExpect(jsonPath("$.language").value("JAVA"))
            .andExpect(jsonPath("$.closed").value(false));
    }

    @Test
    void 토큰이_없으면_401_A001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/review-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"submissionId\": 1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 제출_ID가_없으면_400_C001을_반환한다() throws Exception {
        // when & then
        json(post("/api/review-requests"), requesterToken, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 없는_제출이면_404_R004를_반환한다() throws Exception {
        // when & then
        json(post("/api/review-requests"), requesterToken, "{\"submissionId\": 999999}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R004"));
    }

    @Test
    void 남의_제출이면_403_R003을_반환한다() throws Exception {
        // given
        JudgeJob submission = saveSubmission(requester, Verdict.ACCEPTED);

        // when & then
        json(post("/api/review-requests"), reviewerToken, "{\"submissionId\": " + submission.getId() + "}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R003"));
    }

    @Test
    void 정답이_아닌_제출이면_409_R005를_반환한다() throws Exception {
        // given
        JudgeJob submission = saveSubmission(requester, Verdict.WRONG_ANSWER);

        // when & then
        json(post("/api/review-requests"), requesterToken, "{\"submissionId\": " + submission.getId() + "}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R005"));
    }

    @Test
    void 같은_제출로_다시_요청하면_409_R006을_반환한다() throws Exception {
        // given
        JudgeJob submission = saveSubmission(requester, Verdict.ACCEPTED);
        String body = "{\"submissionId\": " + submission.getId() + "}";
        json(post("/api/review-requests"), requesterToken, body).andExpect(status().isCreated());

        // when & then
        json(post("/api/review-requests"), requesterToken, body)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R006"));
    }

    @Test
    void 요청을_조회하면_200을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        mockMvc.perform(get("/api/review-requests/" + id).header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.requester").value("requester"));
    }

    @Test
    void 없는_요청을_조회하면_404_R001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/review-requests/999999").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    void 목록을_최신순으로_반환하고_문제로_거를_수_있다() throws Exception {
        // given
        createReviewRequest();
        long latest = createReviewRequest();

        // when & then
        mockMvc.perform(get("/api/review-requests").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(latest))
            .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/review-requests").param("problemId", "999999")
                .header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/review-requests").param("size", "101").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 요청자가_닫으면_204이고_리뷰어에게는_목록과_상세에서_사라진다() throws Exception {
        // given
        long id = createReviewRequest();

        // when
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/review-requests/" + id).header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R001"));
        mockMvc.perform(get("/api/review-requests").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/review-requests/" + id).header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(true));
        mockMvc.perform(get("/api/review-requests").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void 요청자가_아니면_닫을_때_403_R008을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R008"));
    }

    @Test
    void 이미_닫은_요청을_다시_닫으면_409_R010을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());

        // when & then
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R010"));
    }

    @Test
    void 제안을_적용하면_바뀐_코드를_200으로_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, "{\"lineNumber\": 2, \"content\": \"수정\", \"suggestedCode\": \"int b = 20;\"}");

        // when & then
        json(post("/api/review-requests/" + id + "/apply"), requesterToken, "{\"lineCommentIds\": [" + commentId + "]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("int a = 1;\nint b = 20;\nint c = 3;\n"));
    }

    @Test
    void 코멘트를_둘_이상_선택해_적용하면_400_R012를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long first = createLineComment(id, "{\"lineNumber\": 2, \"content\": \"가\", \"suggestedCode\": \"x\"}");
        long second = createLineComment(id, "{\"lineNumber\": 2, \"content\": \"나\", \"suggestedCode\": \"y\"}");

        // when & then
        json(post("/api/review-requests/" + id + "/apply"), requesterToken,
            "{\"lineCommentIds\": [" + first + "," + second + "]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("R012"));
    }

    @Test
    void 선택_목록이_없으면_400_C001을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/apply"), requesterToken, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 제안_코드가_없는_코멘트를_적용하면_409_R013을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, "{\"lineNumber\": 1, \"content\": \"설명만\"}");

        // when & then
        json(post("/api/review-requests/" + id + "/apply"), requesterToken, "{\"lineCommentIds\": [" + commentId + "]}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R013"));
    }

    @Test
    void 요청자가_아니면_적용할_때_403_R008을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, "{\"lineNumber\": 1, \"content\": \"수정\", \"suggestedCode\": \"x\"}");

        // when & then
        json(post("/api/review-requests/" + id + "/apply"), reviewerToken, "{\"lineCommentIds\": [" + commentId + "]}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R008"));
    }
}
