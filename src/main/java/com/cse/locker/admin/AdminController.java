package com.cse.locker.admin;

import com.cse.locker.domain.Application;
import com.cse.locker.repo.AdminUserRepository;
import com.cse.locker.repo.ApplicationRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserRepository adminRepo;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    // 승인된 학생 현황 JSON
    @GetMapping("/approved")
    public List<ApprovedDto> approvedList() {
        return applicationRepository.findByStatus(Application.Status.APPROVED)
                .stream()
                .map(a -> new ApprovedDto(
                        a.getLockerNumber(),
                        a.getStudentId(),
                        a.getName(),
                        a.getPhone(),
                        a.getStatus().name()
                ))
                .toList();
    }

    public record ApprovedDto(
            int lockerNumber,
            String studentId,
            String name,
            String phone,
            String status
    ) {}

    // 비밀번호 변경
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            Authentication authentication
    ) {
        AdminUser admin = adminRepo.findByUsername(authentication.getName())
                .orElseThrow();

        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            return ResponseEntity.badRequest().body("현재 비밀번호가 틀렸습니다.");
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepo.save(admin);

        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    // 승인 사물함 CSV 다운로드
    // 3️⃣ 승인 사물함 CSV 다운로드
    // 3️⃣ 승인 사물함 CSV 다운로드
    @GetMapping("/approved/export")
    public void exportApproved(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=approved_lockers.csv");

        // ✅ UTF-8 BOM 추가 (엑셀 한글 깨짐 방지)
        var out = response.getOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        // ✅ OutputStreamWriter로 UTF-8 강제
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.println("사물함번호,학번,이름,전화번호");

        List<Application> approved = applicationRepository.findByStatus(Application.Status.APPROVED);

        for (Application app : approved) {
            // ✅ 값에 쉼표/특수문자 들어가도 안전하게 따옴표 처리
            writer.printf("%d,\"%s\",\"%s\",\"\t%s\"%n",
                    app.getLockerNumber(),
                    app.getStudentId(),
                    app.getName(),
                    app.getPhone()
            );
        }

        writer.flush();
    }

    @PostMapping("/public/change-password")
    public ResponseEntity<String> changePasswordPublic(
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
