package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.ThemeResponse;
import com.example.cortebe.domain.user.dto.UpdateThemeRequest;
import com.example.cortebe.domain.user.entity.ThemeColor;
import com.example.cortebe.domain.user.entity.ThemeMode;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
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
class ThemeServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ThemeService themeService;

    @Test
    void 테마를_고른_적이_없으면_LIGHT와_BLUE를_준다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));

        // when
        ThemeResponse response = themeService.getMyTheme(1L);

        // then
        assertThat(response.mode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(response.color()).isEqualTo(ThemeColor.BLUE);
    }

    @Test
    void 고른_테마가_있으면_그_테마를_준다() {
        // given
        User user = user();
        user.updateTheme(ThemeMode.DARK, ThemeColor.PINK);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        ThemeResponse response = themeService.getMyTheme(1L);

        // then
        assertThat(response.mode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.color()).isEqualTo(ThemeColor.PINK);
    }

    @Test
    void 조회할_때_회원이_없으면_인증_실패() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> themeService.getMyTheme(1L))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 모드만_보내면_강조색은_그대로다() {
        // given
        User user = user();
        user.updateTheme(ThemeMode.LIGHT, ThemeColor.PINK);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        ThemeResponse response = themeService.updateMyTheme(1L, new UpdateThemeRequest(ThemeMode.DARK, null));

        // then
        assertThat(response.mode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.color()).isEqualTo(ThemeColor.PINK);
    }

    @Test
    void 강조색만_보내면_모드는_그대로다() {
        // given
        User user = user();
        user.updateTheme(ThemeMode.DARK, ThemeColor.BLUE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        ThemeResponse response = themeService.updateMyTheme(1L, new UpdateThemeRequest(null, ThemeColor.GREEN));

        // then
        assertThat(response.mode()).isEqualTo(ThemeMode.DARK);
        assertThat(response.color()).isEqualTo(ThemeColor.GREEN);
    }

    @Test
    void 모드와_강조색이_둘_다_없으면_입력_오류() {
        // when & then
        assertThatThrownBy(() -> themeService.updateMyTheme(1L, new UpdateThemeRequest(null, null)))
            .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 변경할_때_회원이_없으면_인증_실패() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> themeService.updateMyTheme(1L, new UpdateThemeRequest(ThemeMode.DARK, null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    private User user() {
        return new User("me@corte.com", "password", "me");
    }
}
