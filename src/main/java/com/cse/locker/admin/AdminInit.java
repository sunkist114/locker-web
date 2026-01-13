package com.cse.locker.admin;

import com.cse.locker.repo.AdminUserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInit {

    private final AdminUserRepository repo;
    private final PasswordEncoder encoder;

    @PostConstruct
    public void init() {
        if (repo.count() == 0) {
            repo.save(new AdminUser("admin", encoder.encode("admin1234")));
            System.out.println("✅ 관리자 계정 생성: admin / admin1234");
        }
    }
}
