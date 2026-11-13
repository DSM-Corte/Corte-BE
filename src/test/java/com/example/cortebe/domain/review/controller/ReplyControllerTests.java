package com.example.cortebe.domain.review.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReplyControllerTests extends ReviewControllerTestSupport {

    private static final String REPLY = "{\"content\": \"답글\"}";

    @Test
    void 요청자가_줄_코멘트에_답글을_만들면_201과_위치를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, "{\"lineNumber\": 1, \"content\": \"코멘트\"}");

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments/" + commentId + "/replies"), requesterToken, REPLY)
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.content").value("답글"))
            .andExpect(jsonPath("$.author").value("requester"));
    }

    @Test
    void 다른_리뷰어가_전체_댓글에_답글을_만들면_201을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);

        // when & then
        json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"), otherReviewerToken, REPLY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value("other"));
    }

    @Test
    void 없는_코멘트에_답글을_만들면_404_R002를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/line-comments/999999/replies"), requesterToken, REPLY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    void 내용이_비어_있으면_400_C001을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);

        // when & then
        json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"), requesterToken, "{\"content\": \"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 닫힌_요청에는_요청자도_답글을_만들_수_없다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());

        // when & then
        json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"), requesterToken, REPLY)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R010"));
    }

    @Test
    void 쓴_사람이_답글을_수정하면_200을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));

        // when & then
        json(patch("/api/review-requests/" + id + "/replies/" + replyId), requesterToken, "{\"content\": \"고침\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("고침"));
    }

    @Test
    void 쓴_사람이_아니면_답글을_수정할_때_403_R009를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));

        // when & then
        json(patch("/api/review-requests/" + id + "/replies/" + replyId), reviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R009"));
    }

    @Test
    void 쓴_사람이_답글을_삭제하면_204를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));

        // when & then
        mockMvc.perform(delete("/api/review-requests/" + id + "/replies/" + replyId)
                .header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());
    }
}
