package com.example.cortebe.global.error.exception;

import lombok.Getter;

@Getter
public class CorteException extends RuntimeException {
    private final ErrorProperty errorCode;
    public CorteException(ErrorProperty errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CorteException(ErrorProperty errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
