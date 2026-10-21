package com.example.cortebe.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CreatePostRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 20000)
    private String content;
}
