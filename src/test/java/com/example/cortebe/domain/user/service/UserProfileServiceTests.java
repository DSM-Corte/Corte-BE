package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.UserProfileResponse;
import com.example.cortebe.domain.user.dto.UserStatsResponse;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.exception.UserNotFoundException;
import com.example.cortebe.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatsService userStatsService;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void 회원이_있으면_닉네임과_그_회원의_통계를_준다() {
        // given
        User user = User.builder().email("a@corte.com").password("password").nickname("민수").build();
        ReflectionTestUtils.setField(user, "id", 3L);
        UserStatsResponse stats = UserStatsResponse.builder().solvedCount(2).build();
        given(userRepository.findById(3L)).willReturn(Optional.of(user));
        given(userStatsService.getMyStats(3L)).willReturn(stats);

        // when
        UserProfileResponse response = userProfileService.get(3L);

        // then
        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.nickname()).isEqualTo("민수");
        assertThat(response.stats()).isSameAs(stats);
    }

    @Test
    void 회원이_없으면_프로필_조회에_실패한다() {
        // given
        given(userRepository.findById(3L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userProfileService.get(3L))
            .isInstanceOf(UserNotFoundException.class);
        then(userStatsService).should(never()).getMyStats(anyLong());
    }
}
