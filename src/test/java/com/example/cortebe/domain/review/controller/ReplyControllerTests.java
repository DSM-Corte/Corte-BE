package com.example.cortebe.domain.review.controller;

import com.example.cortebe.domain.review.repository.ReviewReplyReactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReplyControllerTests extends ReviewControllerTestSupport {

    @Autowired
    private ReviewReplyReactionRepository reactionRepository;

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

    @Test
    void 전체_댓글_작성자가_답글에_반응을_추가하고_취소한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        String replies = "/api/review-requests/" + id + "/replies/";
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));
        String reactions = replies + replyId + "/reactions";

        // when
        ResultActions created = json(post(reactions), reviewerToken, "{\"type\":\"HEART\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("HEART"))
            .andExpect(jsonPath("$.userId").value(reviewer.getId()));
        long reactionId = idOf(created);
        created.andExpect(header().string(HttpHeaders.LOCATION, "http://localhost" + reactions + "/" + reactionId));

        // then
        mockMvc.perform(get("/api/review-requests/" + id + "/comments").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].replies[0].reactionCounts.HEART").value(1))
            .andExpect(jsonPath("$.content[0].replies[0].reactions[0].id").value(reactionId))
            .andExpect(jsonPath("$.content[0].replies[0].reactions[0].userId").value(reviewer.getId()));
        json(patch(replies + replyId), requesterToken, "{\"content\":\"수정\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactionCounts.HEART").value(1));
        mockMvc.perform(delete(reactions + "/" + reactionId).header(HttpHeaders.AUTHORIZATION, otherReviewerToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(reactions + "/" + reactionId).header(HttpHeaders.AUTHORIZATION, reviewerToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/review-requests/" + id + "/comments").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(jsonPath("$.content[0].replies[0].reactionCounts").isEmpty())
            .andExpect(jsonPath("$.content[0].replies[0].reactions").isEmpty());
    }

    @Test
    void 줄_코멘트_답글에는_관리자가_반응을_추가하고_다른_사람의_반응을_취소한다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createLineComment(id, "{\"lineNumber\":1,\"content\":\"코멘트\"}");
        String lineComments = "/api/review-requests/" + id + "/line-comments";
        long replyId = idOf(json(post(lineComments + "/" + commentId + "/replies"), requesterToken, REPLY));
        String reactions = "/api/review-requests/" + id + "/replies/" + replyId + "/reactions";
        String smile = "{\"type\":\"SMILE\"}";

        // when & then
        json(post(reactions), requesterToken, smile).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("R016"));
        mockMvc.perform(post(reactions).contentType("application/json").content(smile))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
        json(post(reactions), reviewerToken, "{\"type\":\"UNKNOWN\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
        json(post(reactions), adminToken, smile).andExpect(status().isCreated());
        long reactionId = idOf(json(post(reactions), reviewerToken, smile).andExpect(status().isCreated()));
        mockMvc.perform(delete(reactions + "/" + reactionId).header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(lineComments).header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(jsonPath("$[0].replies[0].reactionCounts.SMILE").value(1));
    }


    @Test
    void 같은_종류는_중복할_수_없고_다른_종류는_추가할_수_있다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));
        String reactions = "/api/review-requests/" + id + "/replies/" + replyId + "/reactions";
        String thumbsUp = "{\"type\":\"THUMBS_UP\"}";

        // when & then
        json(post(reactions), reviewerToken, thumbsUp).andExpect(status().isCreated());
        json(post(reactions), reviewerToken, "{\"type\":\"CELEBRATION\"}").andExpect(status().isCreated());
        json(post(reactions), reviewerToken, thumbsUp)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R017"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"comments", "line-comments", "replies"})
    void 댓글이나_답글을_삭제하면_반응도_삭제된다(String target) throws Exception {
        // given
        long id = createReviewRequest();
        String root = "/api/review-requests/" + id;
        String commentPath = target.equals("line-comments") ? "line-comments" : "comments";
        long commentId = commentPath.equals("line-comments")
            ? createLineComment(id, "{\"lineNumber\":1,\"content\":\"코멘트\"}") : createComment(id);
        String comment = root + "/" + commentPath + "/" + commentId;
        long replyId = idOf(json(post(comment + "/replies"), requesterToken, REPLY));
        String reply = root + "/replies/" + replyId;
        long reactionId = idOf(json(post(reply + "/reactions"), reviewerToken, "{\"type\":\"HEART\"}"));

        // when
        boolean deleteReply = target.equals("replies");
        mockMvc.perform(delete(deleteReply ? reply : comment)
                .header(HttpHeaders.AUTHORIZATION, deleteReply ? requesterToken : reviewerToken))
            .andExpect(status().isNoContent());

        // then
        reactionRepository.flush();
        assertThat(reactionRepository.existsById(reactionId)).isFalse();
    }

    @Test
    void 닫힌_리뷰의_답글_반응은_관리자도_변경할_수_없다() throws Exception {
        // given
        long id = createReviewRequest();
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            requesterToken, REPLY));
        String reactions = "/api/review-requests/" + id + "/replies/" + replyId + "/reactions";
        String heart = "{\"type\":\"HEART\"}";
        long reactionId = idOf(json(post(reactions), reviewerToken, heart));
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(status().isNoContent());

        // when & then
        json(post(reactions), adminToken, heart).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("R001"));
        mockMvc.perform(delete(reactions + "/" + reactionId).header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("R001"));
        mockMvc.perform(get("/api/review-requests/" + id + "/comments").header(HttpHeaders.AUTHORIZATION, requesterToken))
            .andExpect(jsonPath("$.content[0].replies[0].reactionCounts.HEART").value(1));
    }


    @Test
    void 관리자_본인의_닫힌_리뷰도_반응_추가와_취소는_409다() throws Exception {
        // given
        long id = createReviewRequest(admin, adminToken);
        long commentId = createComment(id);
        long replyId = idOf(json(post("/api/review-requests/" + id + "/comments/" + commentId + "/replies"),
            adminToken, REPLY));
        String reactions = "/api/review-requests/" + id + "/replies/" + replyId + "/reactions";
        String heart = "{\"type\":\"HEART\"}";
        long reactionId = idOf(json(post(reactions), adminToken, heart).andExpect(status().isCreated()));
        mockMvc.perform(post("/api/review-requests/" + id + "/close").header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNoContent());

        // when & then
        json(post(reactions), adminToken, "{\"type\":\"SMILE\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("R010"));
        mockMvc.perform(delete(reactions + "/" + reactionId).header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("R010"));
    }

    @Test
    void 요청과_답글_경로에_속하는_반응만_취소할_수_있다() throws Exception {
        // given
        long id = createReviewRequest();
        long differentId = createReviewRequest();
        long commentId = createComment(id);
        String replies = "/api/review-requests/" + id + "/comments/" + commentId + "/replies";
        long replyId = idOf(json(post(replies), requesterToken, REPLY));
        long differentReply = idOf(json(post(replies), requesterToken, REPLY));
        String reactions = "/api/review-requests/" + id + "/replies/" + replyId + "/reactions";
        String heart = "{\"type\":\"HEART\"}";
        long reactionId = idOf(json(post(reactions), reviewerToken, heart).andExpect(status().isCreated()));

        // when & then
        json(post("/api/review-requests/" + differentId + "/replies/" + replyId + "/reactions"), reviewerToken, heart)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("R002"));
        mockMvc.perform(delete("/api/review-requests/" + id + "/replies/" + differentReply + "/reactions/" + reactionId)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("R015"));
        mockMvc.perform(delete(reactions + "/" + reactionId))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A001"));
    }
}
