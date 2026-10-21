package com.example.cortebe.domain.auth.service;

import com.example.cortebe.domain.auth.dto.SignupRequest;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.exception.UserErrorCode;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.CorteException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CorteException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .nickname(request.nickname())
            .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 위 검사를 둘 다 통과할 수 있어서 DB 유니크 제약으로 한 번 더 막는다
            throw new CorteException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }
}
