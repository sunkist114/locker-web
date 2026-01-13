package com.cse.locker.web;

import com.cse.locker.admin.AdminUser;
import com.cse.locker.repo.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/admin")
public class PublicAdminController {

    private final AdminUserRepository adminRepo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestParam String username,
            @RequestParam String currentPassword,
            @RequestParam String newPassword
    ) {
        AdminUser admin = adminRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("관리자 계정 없음"));

        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            return ResponseEntity.badRequest().body("현재 비밀번호가 틀렸습니다.");
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepo.save(admin);

        return ResponseEntity.ok("비밀번호 변경 완료");
    }
}
