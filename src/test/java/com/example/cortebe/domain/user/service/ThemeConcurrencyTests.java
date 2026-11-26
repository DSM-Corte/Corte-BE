package com.example.cortebe.domain.user.service;

import com.example.cortebe.domain.user.dto.ThemeResponse;
import com.example.cortebe.domain.user.dto.UpdateThemeRequest;
import com.example.cortebe.domain.user.entity.ThemeColor;
import com.example.cortebe.domain.user.entity.ThemeMode;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ThemeConcurrencyTests {

    @Autowired private ThemeService themeService;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void 모드와_강조색을_동시에_바꿔도_둘_다_저장된다() {
        // given
        User user = users.save(new User(UUID.randomUUID() + "@corte.com", "password", "theme"));
        TransactionTemplate first = new TransactionTemplate(transactionManager);
        TransactionTemplate second = new TransactionTemplate(transactionManager);
        second.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            // when
            // 첫 요청이 회원을 읽은 뒤 저장하기 전에, 다른 요청이 강조색을 바꾸고 먼저 커밋한다
            first.executeWithoutResult(status -> {
                themeService.updateMyTheme(user.getId(), new UpdateThemeRequest(ThemeMode.DARK, null));
                second.executeWithoutResult(inner ->
                    themeService.updateMyTheme(user.getId(), new UpdateThemeRequest(null, ThemeColor.PINK)));
            });

            // then
            ThemeResponse saved = themeService.getMyTheme(user.getId());
            assertThat(saved.mode()).isEqualTo(ThemeMode.DARK);
            assertThat(saved.color()).isEqualTo(ThemeColor.PINK);
        } finally {
            users.deleteById(user.getId());
        }
    }
}
