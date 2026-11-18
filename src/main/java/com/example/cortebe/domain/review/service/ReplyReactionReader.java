package com.example.cortebe.domain.review.service;

import com.example.cortebe.domain.review.dto.ReplyReactionResponse;
import com.example.cortebe.domain.review.entity.ReviewReply;
import com.example.cortebe.domain.review.repository.ReviewReplyReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReplyReactionReader {

    private final ReviewReplyReactionRepository repository;

    public Map<Long, List<ReplyReactionResponse>> findByReplyIds(List<ReviewReply> replies) {
        if (replies.isEmpty()) {
            return Map.of();
        }
        return repository.findByReplyIdInOrderByIdAsc(replies.stream().map(ReviewReply::getId).toList())
            .stream().collect(Collectors.groupingBy(reaction -> reaction.getReply().getId(),
                Collectors.mapping(ReplyReactionResponse::from, Collectors.toList())));
    }
}
