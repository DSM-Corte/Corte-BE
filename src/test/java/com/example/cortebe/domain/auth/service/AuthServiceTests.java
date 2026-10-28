package com.example.cortebe.domain.auth.service;

import com.example.cortebe.domain.auth.dto.SignupRequest;
import com.example.cortebe.domain.user.exception.DuplicateEmailException;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    private final SignupRequest request = new SignupRequest("test@corte.com", "password1234", "corte");

    @Test
    void 저장할_때_이메일_유니크_제약에_걸리면_이메일_중복_예외를_던진다() {
        // given
        given(userRepository.saveAndFlush(any()))
            .willThrow(integrityError(ConstraintViolationException.ConstraintKind.UNIQUE,
                "PUBLIC.UK_USERS_EMAIL INDEX PUBLIC.UK_USERS_EMAIL_INDEX_4 ON PUBLIC.USERS(EMAIL)"));

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
            .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void 이메일_유니크_제약이_아닌_무결성_오류는_그대로_던진다() {
        // given
        given(userRepository.saveAndFlush(any()))
            .willThrow(integrityError(ConstraintViolationException.ConstraintKind.NOT_NULL, null));

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private DataIntegrityViolationException integrityError(ConstraintViolationException.ConstraintKind kind,
                                                           String constraintName) {
        return new DataIntegrityViolationException("무결성 오류",
            new ConstraintViolationException("무결성 오류", new SQLException(), kind, constraintName));
    }
}
