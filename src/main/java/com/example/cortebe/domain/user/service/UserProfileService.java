package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.UserProfileResponse;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.exception.UserNotFoundException;
import com.example.cortebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserStatsService userStatsService;

    public UserProfileResponse get(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        // 통계는 내 통계와 같은 기준으로 계산한다
        return UserProfileResponse.from(user, userStatsService.getPublicStats(userId));
    }
}
