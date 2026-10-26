package com.example.cortebe.global.error.exception;

public class InvalidInputException extends CorteException {

    public InvalidInputException() {
        super(GlobalErrorCode.INVALID_INPUT);
    }
}
