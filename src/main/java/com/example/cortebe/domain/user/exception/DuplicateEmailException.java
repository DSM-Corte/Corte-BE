package com.example.cortebe.domain.user.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class DuplicateEmailException extends CorteException {

    public DuplicateEmailException() {
        super(UserErrorCode.DUPLICATE_EMAIL);
    }
}
