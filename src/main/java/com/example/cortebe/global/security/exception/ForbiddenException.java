package com.example.cortebe.global.security.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ForbiddenException extends CorteException {

    public ForbiddenException() {
        super(AuthErrorCode.FORBIDDEN);
    }
}
