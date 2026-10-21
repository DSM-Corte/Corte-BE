package com.example.cortebe.domain.user.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class LoginFailedException extends CorteException {

    public LoginFailedException() {
        super(UserErrorCode.LOGIN_FAILED);
    }
}
