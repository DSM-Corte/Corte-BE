package com.example.cortebe.domain.problem.dto;

import com.example.cortebe.domain.judge.runner.Language;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.Map;

@Getter
@NoArgsConstructor
public class CreateProblemRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 20000)
    private String description;

    @NotBlank
    @Size(max = 50)
    private String category;

    @NotNull
    @Positive
    private Integer memoryLimitMb;

    @NotNull
    @Positive
    private Integer timeLimitMs;

    @Size(max = 6)
    private Map<@NotNull Language, @NotNull @Min(6) @Max(1024) Integer> languageMemoryLimitsMb;

    @Size(max = 10)
    private Set<@NotBlank @Size(max = 30) String> tags;
}
