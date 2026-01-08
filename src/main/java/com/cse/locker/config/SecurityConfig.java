package com.cse.locker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpStatus;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;

@Configuration
public class SecurityConfig {

    @Value("${app.admin.username:admin}")
    private String adminUser;

    @Value("${app.admin.password:admin1234}")
    private String adminPass;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUser)
                        .password(encoder.encode(adminPass))
                        .roles("ADMIN")
                        .build()
        );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.disable());

        // ✅ API는 인증 없으면 401
        http.exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")
                )
        );

        http.authorizeHttpRequests(auth -> auth
                // ✅ 정적 리소스(css/js/images 등) - 안전한 권장 방식
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                // ✅ 학생 화면은 누구나
                .requestMatchers("/student.html").permitAll()

                // ✅ 관리자 화면은 로그인 필요
                .requestMatchers("/admin.html").hasRole("ADMIN")

                // ✅ 공개 API
                .requestMatchers("/api/public/**").permitAll()

                // ✅ 관리자 API
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // SSE도 관리자만
                .requestMatchers("/sse/**").hasRole("ADMIN")

                // 그 외는 기본 허용(원하면 denyAll로 바꿔도 됨)
                .anyRequest().permitAll()
        );

        http.formLogin(form -> form.permitAll());
        http.logout(logout -> logout.permitAll());

        return http.build();
    }
}
