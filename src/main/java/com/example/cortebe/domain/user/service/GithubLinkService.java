package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.GithubLinkResponse;
import com.example.cortebe.domain.user.dto.LinkGithubRequest;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GithubLinkService {

    private final UserRepository userRepository;

    @Transactional
    public GithubLinkResponse link(Long userId, LinkGithubRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(UnauthorizedException::new);
        user.linkGithub(request.installationId(), request.owner().strip(), request.repo().strip());
        return GithubLinkResponse.from(user);
    }
}
