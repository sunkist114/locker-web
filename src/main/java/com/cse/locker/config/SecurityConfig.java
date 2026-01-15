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
                        // ✅ 누구나 접근 가능 (여기가 먼저!)
                        .requestMatchers(
                                "/", "/student.html", "/my-locker.html",
                                "/login.html", "/admin.html", "/admin-approved.html",
                                "/css/**", "/js/**", "/images/**",
                                "/api/public/**"          // ✅ public API는 모두 열기
                        ).permitAll()

                        // ✅ 관리자만
                        .requestMatchers(
                                "/admin.html", "/admin-approved.html",
                                "/api/admin/**"
                        ).hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/admin.html", true)   // ✅ true 없어야 함
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
