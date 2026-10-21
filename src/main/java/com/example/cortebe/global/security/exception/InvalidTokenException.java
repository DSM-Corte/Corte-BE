package com.example.cortebe.global.security.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class InvalidTokenException extends CorteException {

    public InvalidTokenException() {
        super(AuthErrorCode.INVALID_TOKEN);
    }
}
