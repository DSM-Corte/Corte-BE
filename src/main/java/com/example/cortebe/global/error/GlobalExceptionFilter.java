package com.example.cortebe.global.error;

import com.example.cortebe.global.error.exception.ErrorProperty;
import com.example.cortebe.global.error.exception.GlobalErrorCode;
import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.error.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            if (response.isCommitted()) {
                log.error("Exception after response committed: {} {}", request.getMethod(), request.getRequestURI(), e);
                throw e;
            }
            switch (e) {
                case CorteException ce -> {
                    log.warn("{}: {} {}", ce.getClass().getSimpleName(), ce.getErrorCode().getStatus(), ce.getErrorCode().getMessage());
                    writeErrorResponse(response, ce.getErrorCode(), ce.getMessage());
                }
                case Exception ex when ex.getCause() instanceof CorteException ce -> {
                    log.warn("{}: {} {}", ce.getClass().getSimpleName(), ce.getErrorCode().getStatus(), ce.getErrorCode().getMessage());
                    writeErrorResponse(response, ce.getErrorCode(), ce.getMessage());
                }
                default -> {
                    log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), e);
                    writeErrorResponse(response, GlobalErrorCode.INTERNAL_ERROR, GlobalErrorCode.INTERNAL_ERROR.getMessage());
                }
            }
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorProperty errorCode, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode, message));
    }
}
