package com.example.cortebe.domain.user.repository;

import com.example.cortebe.domain.user.entity.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail() {
        // given
        userRepository.save(new User("test@corte.com", "password", "corte"));

        // when & then
        assertThat(userRepository.findByEmail("test@corte.com")).isPresent();
        assertThat(userRepository.existsByEmail("test@corte.com")).isTrue();
    }

    @Test
    void duplicateEmail() {
        // given
        userRepository.saveAndFlush(new User("test@corte.com", "password", "corte"));

        // when & then
        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("test@corte.com", "password", "corte")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 이메일이_중복되면_uk_users_email_제약에_걸린다() {
        // given
        userRepository.saveAndFlush(new User("test@corte.com", "password", "corte"));

        // when & then
        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("test@corte.com", "password", "corte")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .cause()
            .isInstanceOfSatisfying(ConstraintViolationException.class, cause ->
                assertThat(cause.getConstraintName()).containsIgnoringCase("uk_users_email"));
    }
}
