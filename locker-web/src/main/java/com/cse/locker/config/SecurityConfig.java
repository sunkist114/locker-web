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
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            AdminUser admin = adminRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("관리자 계정 없음"));

            return User.builder()
                    .username(admin.getUsername())
                    .password(admin.getPassword())
                    .roles("ADMIN")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/student.html",
                                "/login.html",
                                "/admin-password.html",          // ✅ 로그인 없이 접근 허용
                                "/api/public/**",
                                "/css/**", "/js/**"
                        ).permitAll()
                        .requestMatchers(
                                "/admin.html",
                                "/admin-approved.html",
                                "/api/admin/**"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")         // ⭐ 로그인 처리 URL
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
