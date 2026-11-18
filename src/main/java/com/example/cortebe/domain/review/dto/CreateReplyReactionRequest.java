package com.example.cortebe.domain.review.dto;

import com.example.cortebe.domain.review.entity.ReplyReactionType;
import jakarta.validation.constraints.NotNull;

public record CreateReplyReactionRequest(@NotNull ReplyReactionType type) {
}
