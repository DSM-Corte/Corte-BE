package com.example.cortebe.domain.problem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor
public class UpdateProblemRequest {

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 200)
    private String title;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 20000)
    private String description;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 50)
    private String category;

    @Positive
    private Integer memoryLimitMb;

    @Positive
    private Integer timeLimitMs;

    @Size(max = 10)
    private Set<@NotBlank @Size(max = 30) String> tags;
}
