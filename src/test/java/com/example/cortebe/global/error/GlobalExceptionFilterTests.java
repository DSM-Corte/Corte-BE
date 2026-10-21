package com.example.cortebe.global.error;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionFilterTests {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final GlobalExceptionFilter filter = new GlobalExceptionFilter(objectMapper);

    @Test
    void 전송이_시작된_응답과_기존_예외를_유지한다() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getWriter().write("already sent");
        response.flushBuffer();
        ServletException failure = new ServletException("stream failed");

        ServletException thrown = assertThrows(ServletException.class,
            () -> filter.doFilter(request, response, (req, res) -> { throw failure; }));

        assertSame(failure, thrown);
        assertEquals("already sent", response.getContentAsString());
        assertEquals(200, response.getStatus());
    }

    @Test
    void 전송_전_응답_본문을_비즈니스_오류_응답으로_교체한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getWriter().write("partial");

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            throw new CorteException(GlobalErrorCode.INVALID_INPUT);
        });

        assertEquals(400, response.getStatus());
        assertEquals("C001", objectMapper.readTree(response.getContentAsString()).get("code").asText());
    }

    @Test
    void 예상하지_못한_예외의_내부_메시지를_응답에_노출하지_않는다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            throw new IllegalStateException("internal detail");
        });

        assertEquals(500, response.getStatus());
        assertEquals(GlobalErrorCode.INTERNAL_ERROR.getMessage(),
            objectMapper.readTree(response.getContentAsString()).get("message").asText());
    }
}
