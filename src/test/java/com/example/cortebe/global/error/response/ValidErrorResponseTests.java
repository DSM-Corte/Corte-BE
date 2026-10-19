package com.example.cortebe.global.error.response;

import com.example.cortebe.global.error.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValidErrorResponseTests {

    @Test
    void validationResponseDoesNotEchoRejectedValue() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "password", "secret-value", false,
            null, null, "invalid format"));

        ValidErrorResponse response = ValidErrorResponse.of(GlobalErrorCode.INVALID_INPUT, binding);

        assertEquals(1, response.errors().size());
        assertEquals("password", response.errors().getFirst().field());
        assertNull(response.errors().getFirst().value());
        assertEquals("invalid format", response.errors().getFirst().reason());
    }
}
