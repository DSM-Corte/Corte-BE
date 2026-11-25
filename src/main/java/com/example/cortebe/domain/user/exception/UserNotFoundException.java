package com.example.cortebe.domain.user.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class UserNotFoundException extends CorteException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
