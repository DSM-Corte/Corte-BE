package com.example.cortebe.domain.auth.service;

import com.example.cortebe.domain.auth.AdminProperties;
import com.example.cortebe.domain.auth.dto.LoginRequest;
import com.example.cortebe.domain.auth.dto.SignupRequest;
import com.example.cortebe.domain.auth.dto.TokenResponse;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.exception.DuplicateEmailException;
import com.example.cortebe.domain.user.exception.LoginFailedException;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.Role;
import com.example.cortebe.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AdminProperties adminProperties;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .nickname(request.nickname())
            .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 위 검사를 둘 다 통과할 수 있어서 DB 유니크 제약으로 한 번 더 막는다
            if (isEmailUniqueViolation(e)) {
                throw new DuplicateEmailException();
            }
            throw e;
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new LoginFailedException());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new LoginFailedException();
        }

        Role role = adminProperties.isAdmin(user.getEmail()) ? Role.ADMIN : Role.USER;
        return new TokenResponse(jwtProvider.createAccessToken(user.getId(), role));
    }

    private boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cause)) {
            return false;
        }

        String constraintName = cause.getConstraintName();
        if (constraintName == null) {
            return false;
        }

        // DB마다 이름 앞뒤에 다른 글자가 붙어서(H2: "PUBLIC.UK_USERS_EMAIL INDEX ...") 이름이 들어 있는지만 본다
        return constraintName.toLowerCase().contains(EMAIL_UNIQUE_CONSTRAINT);
    }
}
