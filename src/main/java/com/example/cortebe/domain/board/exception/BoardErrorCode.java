package com.example.cortebe.domain.board.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BoardErrorCode implements ErrorProperty {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "게시글을 찾을 수 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "B002", "게시글 작성자만 수정하거나 삭제할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
