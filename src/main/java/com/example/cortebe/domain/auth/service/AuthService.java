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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AdminProperties adminProperties;

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
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 위 검사를 둘 다 통과할 수 있어서 DB 유니크 제약으로 한 번 더 막는다
            throw new DuplicateEmailException();
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new LoginFailedException());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new LoginFailedException();
        }

        Role role = adminProperties.isAdmin(user.getEmail()) ? Role.ADMIN : Role.USER;
        return new TokenResponse(jwtProvider.createAccessToken(user.getId(), role));
    }
}
