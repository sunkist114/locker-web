package com.cse.locker.config;

import com.cse.locker.admin.AdminUser;
import com.cse.locker.repo.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminUserRepository adminRepo;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호는 BCrypt 해시로 저장/검증
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 로그인 시 username으로 DB에서 AdminUser를 찾아 Security User로 변환
        return username -> {
            AdminUser admin = adminRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("관리자 계정 없음"));

            return User.builder()
                    .username(admin.getUsername())
                    .password(admin.getPassword()) // 이미 해시된 비밀번호
                    .roles("ADMIN")                // ROLE_ADMIN 권한으로 처리됨
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // (편의) CSRF 비활성화: 폼/POST 요청을 세션 기반으로 쓸 땐 주의 필요
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/student", "/student.html", "/my-locker.html",
                                "/login.html",
                                "/oauth/kakao/**",          // ⭐ 추가: 카카오 OAuth 전체 공개
                                "/error",                   // (권장) 에러 페이지
                                "/favicon.ico",
                                "/css/**", "/js/**", "/images/**",
                                "/assets/**", "/static/**", // (권장) 정적 경로
                                "/api/public/**",
                                "/service-worker.js",       // Web Push Service Worker
                                "/api/push/**"              // Web Push subscribe/vapid-key
                        ).permitAll()

                        .requestMatchers(
                                "/admin.html", "/admin-approved.html",
                                "/api/admin/**"
                        ).hasRole("ADMIN")

                        .anyRequest().authenticated()
                )


                .formLogin(form -> form
                        // 커스텀 로그인 페이지(정적 html)
                        .loginPage("/login.html")
                        // 로그인 폼 action이 POST /login 으로 가게 될 때 처리
                        .loginProcessingUrl("/login")
                        // 로그인 성공 시 관리자 페이지로 이동
                        .defaultSuccessUrl("/admin.html", true)
                        .failureUrl("/login.html?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html")
                );

        return http.build();
    }
}
