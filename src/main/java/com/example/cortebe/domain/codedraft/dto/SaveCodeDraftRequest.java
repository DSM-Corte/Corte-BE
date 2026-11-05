package com.example.cortebe.domain.codedraft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SaveCodeDraftRequest(
    @NotNull @Size(max = 102400) @JsonDeserialize(using = CodeDraftCodeDeserializer.class) String code
) {
}
