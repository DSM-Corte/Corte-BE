package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.ThemeResponse;
import com.example.cortebe.domain.user.dto.UpdateThemeRequest;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeService {

    private final UserRepository userRepository;

    public ThemeResponse getMyTheme(Long userId) {
        return ThemeResponse.from(findUser(userId));
    }

    @Transactional
    public ThemeResponse updateMyTheme(Long userId, UpdateThemeRequest request) {
        if (request.mode() == null && request.color() == null) {
            throw new InvalidInputException();
        }

        User user = findUser(userId);
        user.updateTheme(request.mode(), request.color());
        return ThemeResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(UnauthorizedException::new);
    }
}
