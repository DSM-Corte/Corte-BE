package com.example.cortebe.domain.review.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineCommentControllerTests extends ReviewControllerTestSupport {

    private static final String COMMENT = "{\"lineNumber\": 2, \"content\": \"```java\\nint b;\\n```\", \"suggestedCode\": \"int b = 20;\"}";

    @Test
    void 리뷰어가_줄_코멘트를_만들면_201과_위치를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments"), reviewerToken, COMMENT)
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.lineNumber").value(2))
            .andExpect(jsonPath("$.suggestedCode").value("int b = 20;"))
            .andExpect(jsonPath("$.author").value("reviewer"));
    }

    @Test
    void 토큰이_없으면_401_A001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/review-requests/1/line-comments").contentType(MediaType.APPLICATION_JSON).content(COMMENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 줄_번호가_0이면_400_C001을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments"), reviewerToken, "{\"lineNumber\": 0, \"content\": \"내용\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 코드_줄_수를_넘으면_400_R011을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments"), reviewerToken, "{\"lineNumber\": 4, \"content\": \"내용\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("R011"));
    }

    @Test
    void 요청자가_줄_코멘트를_만들면_403_R007을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments"), requesterToken, COMMENT)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R007"));
    }

    @Test
    void 줄_코멘트_목록은_줄_번호순으로_답글과_함께_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        createLineComment(id, "{\"lineNumber\": 3, \"content\": \"세번째\"}");
        long second = createLineComment(id, COMMENT);
        json(post("/api/review-requests/" + id + "/line-comments/" + second + "/replies"), requesterToken,
            "{\"content\": \"고마워요\"}").andExpect(status().isCreated());

        // when & then
        mockMvc.perform(get("/api/review-requests/" + id + "/line-comments").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].lineNumber").value(2))
            .andExpect(jsonPath("$[0].replies[0].content").value("고마워요"))
            .andExpect(jsonPath("$[1].replies.length()").value(0));
    }

    @Test
    void 쓴_사람이_수정하면_200을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);

        // when & then
        json(patch("/api/review-requests/" + id + "/line-comments/" + commentId), reviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("고침"))
            .andExpect(jsonPath("$.suggestedCode").value("int b = 20;"));
    }

    @Test
    void 쓴_사람이_아니면_수정할_때_403_R009를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);

        // when & then
        json(patch("/api/review-requests/" + id + "/line-comments/" + commentId), otherReviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R009"));
    }

    @Test
    void 공백만_있는_내용으로_수정하면_400_C001을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);

        // when & then
        json(patch("/api/review-requests/" + id + "/line-comments/" + commentId), reviewerToken, "{\"content\": \" \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 닫힌_요청의_코멘트는_요청자만_조회하고_수정은_쓴_사람이_아니라_403이다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());

        // when & then
        mockMvc.perform(get("/api/review-requests/" + id + "/line-comments").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/review-requests/" + id + "/line-comments").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNotFound());
        json(patch("/api/review-requests/" + id + "/line-comments/" + commentId), requesterToken, "{\"content\": \"고침\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R009"));
        json(patch("/api/review-requests/" + id + "/line-comments/" + commentId), reviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    void 쓴_사람이_삭제하면_204이고_답글도_함께_사라진다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);
        json(post("/api/review-requests/" + id + "/line-comments/" + commentId + "/replies"), requesterToken,
            "{\"content\": \"답글\"}").andExpect(status().isCreated());

        // when
        mockMvc.perform(delete("/api/review-requests/" + id + "/line-comments/" + commentId)
                .header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/review-requests/" + id + "/line-comments").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 쓴_사람이_아니면_삭제할_때_403_R009를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, COMMENT);

        // when & then
        mockMvc.perform(delete("/api/review-requests/" + id + "/line-comments/" + commentId)
                .header(HttpHeaders.AUTHORIZATION, otherReviewerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R009"));
    }
}
