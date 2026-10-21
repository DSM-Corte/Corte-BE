package com.example.cortebe.domain.board.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UpdatePostRequest {

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 200)
    private String title;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 20000)
    private String content;
}
