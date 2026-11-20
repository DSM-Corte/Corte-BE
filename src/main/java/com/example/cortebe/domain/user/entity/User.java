package com.example.cortebe.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

// 모드와 강조색을 동시에 따로 바꿔도 서로의 변경을 덮어쓰지 않도록 바뀐 컬럼만 UPDATE한다
@DynamicUpdate
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 60)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    // 기존 회원 행이 있어 빈 값을 허용한다. 비어 있으면 응답에서 기본값을 쓴다
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ThemeMode themeMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ThemeColor themeColor;

    @Builder
    public User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public void updateTheme(ThemeMode themeMode, ThemeColor themeColor) {
        if (themeMode != null) {
            this.themeMode = themeMode;
        }
        if (themeColor != null) {
            this.themeColor = themeColor;
        }
    }
}
