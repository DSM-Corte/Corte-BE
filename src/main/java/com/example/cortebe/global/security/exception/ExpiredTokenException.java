package com.example.cortebe.global.security.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class ExpiredTokenException extends CorteException {

    public ExpiredTokenException() {
        super(AuthErrorCode.EXPIRED_TOKEN);
    }
}
