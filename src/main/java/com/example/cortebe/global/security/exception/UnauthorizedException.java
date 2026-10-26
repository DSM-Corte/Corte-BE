package com.example.cortebe.global.security.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class UnauthorizedException extends CorteException {

    public UnauthorizedException() {
        super(AuthErrorCode.UNAUTHORIZED);
    }
}
