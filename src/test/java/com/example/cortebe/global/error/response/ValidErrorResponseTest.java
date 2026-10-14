package com.example.cortebe.global.error.response;

import com.example.cortebe.global.error.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;

class ValidErrorResponseTest {

    @Test
    void 검증_실패한_필드의_입력값을_응답에_담는다() {
        // given
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "title", "", false, null, null, "제목은 비어 있을 수 없습니다."));
        bindingResult.addError(new FieldError("request", "memo", null, false, null, null, "메모는 필수입니다."));

        // when
        ValidErrorResponse response = ValidErrorResponse.of(GlobalErrorCode.INVALID_INPUT, bindingResult);

        // then
        assertThat(response.errors()).containsExactly(
            new ValidErrorResponse.FieldErrorDetail("title", "", "제목은 비어 있을 수 없습니다."),
            new ValidErrorResponse.FieldErrorDetail("memo", null, "메모는 필수입니다."));
    }
}
