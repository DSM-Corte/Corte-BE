package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.GithubLinkResponse;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GithubLinkServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GithubLinkService githubLinkService;

    @Test
    void 회원이_있으면_저장된_연동_정보를_준다() {
        // given
        User user = User.builder().email("a@corte.com").password("password").nickname("a").build();
        user.linkGithub(3L, "corte", "solutions");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        GithubLinkResponse response = githubLinkService.get(1L);

        // then
        assertThat(response.installationId()).isEqualTo(3L);
        assertThat(response.owner()).isEqualTo("corte");
        assertThat(response.repo()).isEqualTo("solutions");
    }

    @Test
    void 회원이_없으면_연동_정보_조회에_실패한다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> githubLinkService.get(1L))
            .isInstanceOf(UnauthorizedException.class);
    }
}
