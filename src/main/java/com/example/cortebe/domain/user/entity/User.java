package com.example.cortebe.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

// 모드와 강조색을 동시에 따로 바꿔도 서로의 변경을 덮어쓰지 않도록 바뀐 컬럼만 UPDATE한다
@DynamicUpdate
@Entity
@EntityListeners(AuditingEntityListener.class)
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

    // 기존 회원 행이 있어 빈 값을 허용한다. 세 값이 모두 있어야 GitHub 연동으로 본다
    private Long githubInstallationId;

    @Column(length = 100)
    private String githubOwner;

    @Column(length = 100)
    private String githubRepo;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime joinedAt;

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

    public void linkGithub(long installationId, String owner, String repo) {
        this.githubInstallationId = installationId;
        this.githubOwner = owner;
        this.githubRepo = repo;
    }

    public boolean isGithubLinked() {
        return githubInstallationId != null && githubOwner != null && githubRepo != null;
    }
}
