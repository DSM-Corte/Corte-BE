package com.example.cortebe.domain.board.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 200) String title,
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000) String content
) {
}
