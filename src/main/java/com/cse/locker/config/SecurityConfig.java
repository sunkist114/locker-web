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
                        // 누구나 접근 가능: 학생 페이지/로그인/정적 리소스/공개 API
                        .requestMatchers(
                                "/", "/student.html", "/my-locker.html",
                                "/login.html",
                                "/css/**", "/js/**", "/images/**",
                                "/api/public/**"
                        ).permitAll()

                        // 관리자만 접근 가능: 관리자 페이지 + 관리자 API
                        .requestMatchers(
                                "/admin.html", "/admin-approved.html",
                                "/api/admin/**"
                        ).hasRole("ADMIN")

                        // 나머지는 로그인 필요
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
