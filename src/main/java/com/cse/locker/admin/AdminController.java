package com.cse.locker.admin;

// 프로젝트 도메인/DB 접근에 필요한 클래스들 import
import com.cse.locker.domain.Application;                 // 사물함 신청 데이터
import com.cse.locker.repo.AdminUserRepository;           // 관리자 계정 DB 접근용
import com.cse.locker.repo.ApplicationRepository;         // 신청 DB 접근용 레포지토리

// HTTP 응답을 직접 다루기 위한 클래스
import jakarta.servlet.http.HttpServletResponse;

// 중복 기능
import lombok.RequiredArgsConstructor;

// Spring Web / Spring Security 관련
import org.springframework.http.ResponseEntity;           // HTTP 응답을 반환
import org.springframework.security.core.Authentication;  // 로그인한 관리자 정보를 가져올 때
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 해시 비교/생성
import org.springframework.web.bind.annotation.*;

// CSV를 UTF-8로 쓰기 위한 도구
import java.io.OutputStreamWriter;                        // OutputStream을 문자 스트림으로 바꿔줌
import java.nio.charset.StandardCharsets;                 // UTF-8 같은 인코딩 상수 제공

// 입출력/컬렉션
import java.io.IOException;                               // 파일/스트림 처리 중 예외
import java.io.PrintWriter;                               // 텍스트를 쉽게 출력하는 Writer
import java.util.List;                                    // 리스트

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    // 관리자 계정 테이블 접근용 레포지토리
    private final AdminUserRepository adminRepo;
    // 신청 테이블 접근용 Repository
    private final ApplicationRepository applicationRepository;
    // 비밀번호를 "암호화/검증"하기 위한 도구
    private final PasswordEncoder passwordEncoder;


    // 승인된 학생 현황 JSON
    @GetMapping("/approved")
    public List<ApprovedDto> approvedList() {

        // status가 APPROVED인 신청만 가져옴
        return applicationRepository.findByStatus(Application.Status.APPROVED)

                // 가져온 어플리케이션을 stream으로 순회하면서
                .stream()

                // 각 어플리케이션을 ApprovedDto로 변환
                .map(a -> new ApprovedDto(
                        a.getLockerNumber(),
                        a.getStudentId(),
                        a.getName(),
                        a.getPhone(),
                        a.getStatus().name()
                ))

                // stream 결과를 다시 List로 수집해서 반환
                .toList();
    }

    // 승인 목록을 JSON으로 내려줄 때 쓰는 데이터 묶음
    public record ApprovedDto(
            int lockerNumber,
            String studentId,
            String name,
            String phone,
            String status
    ) {}


    // 비밀번호 변경 API
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestParam String currentPassword,       // 현재 비밀번호
            @RequestParam String newPassword,           // 새 비밀번호
            Authentication authentication               // Spring Security가 넣어주는 "현재 로그인 정보"
    ) {
        // 지금 로그인한 사용자의 username을 꺼내서 DB에서 그 관리자 계정을 찾아옴.
        AdminUser admin = adminRepo.findByUsername(authentication.getName())
                .orElseThrow(); // 없으면 예외

        // 사용자가 입력한 "현재 비밀번호"가 DB에 저장된 해시와 일치하는지 검사
        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            // 틀리면 400 Bad Request로 메시지 반환
            return ResponseEntity.badRequest().body("현재 비밀번호가 틀렸습니다.");
        }

        // 새 비밀번호를 해시로 바꿔서 저장해야 함.
        admin.setPassword(passwordEncoder.encode(newPassword));

        // DB에 업데이트 반영
        adminRepo.save(admin);

        // 성공 메시지 반환
        return ResponseEntity.ok("비밀번호 변경 완료");
    }


    // 사물함 이용자 목록을 CSV로 다운로드하는 API
    @GetMapping("/approved/export")
    public void exportApproved(HttpServletResponse response) throws IOException {

        // 응답의 Context-Type을 CSV로 설정
        response.setContentType("text/csv; charset=UTF-8");

        // 응답 인코딩을 UTF-8로 명시
        response.setCharacterEncoding("UTF-8");

        // 브라우저가 "파일 다운로드"로 처리하도록 헤더 설정
        response.setHeader("Content-Disposition", "attachment; filename=approved_lockers.csv");

        // UTF-8 BOM 추가 (엑셀 한글 깨짐 방지)
        var out = response.getOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        // OutputStreamWriter로 UTF-8 강제
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.println("사물함번호,학번,이름,전화번호");

        // 승인된 신청자들을 DB에서 가져옴
        List<Application> approved = applicationRepository.findByStatus(Application.Status.APPROVED);

        // 한 줄씩 CSV로 기록
        for (Application app : approved) {
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
        // username으로 관리자 계정을 찾음
        AdminUser admin = adminRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("관리자 계정 없음"));

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            return ResponseEntity.badRequest().body("현재 비밀번호가 틀렸습니다.");
        }

        // 새 비밀번호 해시 후 저장
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepo.save(admin);

        // 성공 메시지
        return ResponseEntity.ok("비밀번호 변경 완료");
    }
}
