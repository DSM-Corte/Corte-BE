package com.example.cortebe.global.config;

import com.example.cortebe.global.error.exception.CorteException;
import com.example.cortebe.global.security.exception.AuthErrorCode;
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
                .requestMatchers(HttpMethod.POST, "/api/executions", "/api/submissions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/executions/*", "/api/submissions/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/problems", "/api/problems/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/problems", "/api/problems/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/problems", "/api/problems/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, e) -> {
                    throw new CorteException(AuthErrorCode.UNAUTHORIZED);
                }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
