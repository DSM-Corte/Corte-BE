package com.example.cortebe.domain.review.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerTests extends ReviewControllerTestSupport {

    @Test
    void 리뷰어가_전체_댓글을_만들면_201과_위치를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/comments"), reviewerToken, "{\"content\": \"```python\\nprint(1)\\n```\"}")
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.content").value("```python\nprint(1)\n```"))
            .andExpect(jsonPath("$.author").value("reviewer"));
    }

    @Test
    void 내용이_비어_있으면_400_C001을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/comments"), reviewerToken, "{\"content\": \" \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 요청자가_전체_댓글을_만들면_403_R007을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(post("/api/review-requests/" + id + "/comments"), requesterToken, "{\"content\": \"내용\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R007"));
    }

    @Test
    void 댓글_목록은_최신순_페이지로_답글과_함께_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        createComment(id);
        long latest = createComment(id);
        json(post("/api/review-requests/" + id + "/comments/" + latest + "/replies"), requesterToken,
            "{\"content\": \"답글\"}").andExpect(status().isCreated());

        // when & then
        mockMvc.perform(get("/api/review-requests/" + id + "/comments").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(latest))
            .andExpect(jsonPath("$.content[0].replies[0].content").value("답글"))
            .andExpect(jsonPath("$.content[1].replies.length()").value(0));
    }

    @Test
    void 쓴_사람이_수정하면_200을_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);

        // when & then
        json(patch("/api/review-requests/" + id + "/comments/" + commentId), reviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("고침"));
    }

    @Test
    void 쓴_사람이_아니면_수정할_때_403_R009를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);

        // when & then
        json(patch("/api/review-requests/" + id + "/comments/" + commentId), otherReviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R009"));
    }

    @Test
    void 없는_댓글을_수정하면_404_R002를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();

        // when & then
        json(patch("/api/review-requests/" + id + "/comments/999999"), reviewerToken, "{\"content\": \"고침\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    void 쓴_사람이_삭제하면_204를_반환한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);

        // when & then
        mockMvc.perform(delete("/api/review-requests/" + id + "/comments/" + commentId)
                .header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/review-requests/" + id + "/comments").header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
