package com.example.cortebe.global.config;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import com.example.cortebe.global.security.exception.ForbiddenException;
import com.example.cortebe.global.security.jwt.JwtAuthenticationFilter;
import com.example.cortebe.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/workbooks", "/api/workbooks/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/workbooks", "/api/workbooks/**").permitAll()
                .requestMatchers("/api/workbooks", "/api/workbooks/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/executions", "/api/submissions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/executions/*", "/api/submissions/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/problems", "/api/problems/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/leaderboard").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/problems", "/api/problems/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/profile").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                // 연동 정보는 관리자만 쓰는 기능이라 조회도 저장과 같은 권한으로 막는다
                .requestMatchers(HttpMethod.GET, "/api/users/me/github").hasRole("ADMIN")
                // 다른 계정의 설치 ID를 저장해 그 레포에 쓰는 것을 막으려고 관리자만 허용한다
                .requestMatchers(HttpMethod.PUT, "/api/users/me/github").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, e) -> {
                    throw new CorteException(AuthErrorCode.UNAUTHORIZED);
                })
                // 로그인했지만 역할이 맞지 않으면 톰캣 HTML 대신 공통 JSON 오류(403 A004)로 응답한다
                .accessDeniedHandler((request, response, e) -> {
                    throw new ForbiddenException();
                }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
