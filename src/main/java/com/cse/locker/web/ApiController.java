package com.cse.locker.web;

import com.cse.locker.service.LockerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@Validated
public class ApiController {

    private final LockerService service;
    private final SseController sse;

    public ApiController(LockerService service, SseController sse) {
        this.service = service;
        this.sse = sse;
    }

    // -----------------------
    // Public
    // -----------------------

    @GetMapping("/api/public/lockers")
    public List<LockerService.LockerDto> lockers() {
        return service.getLockerGrid();
    }

    // ✅ 카카오 연동 여부 확인
    @GetMapping("/api/public/kakao-linked")
    public ResponseEntity<?> kakaoLinked(@RequestParam String studentId) {
        boolean linked = service.isKakaoLinked(studentId.trim());
        return ResponseEntity.ok(Map.of("linked", linked));
    }

    public record ApplyReq(String studentId, String name, String phone, int lockerNumber) {}

    /**
     * ✅ 학생 신청 + 송금(보증금) 이미지 업로드 (multipart)
     * - data(JSON) + transferImage(file)
     *
     * ✅ 정책:
     * 1) 카카오 연동이 되어있어야 신청 가능(409)
     * 2) 신청 성공 시 "확인코드"는 발급되지 않음(승인 시점에 발급/발송)
     * 3) 신청 성공 후 SSE broadcast
     */
    @PostMapping(value = "/api/public/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> apply(
            @RequestPart("data") ApplyReq req,
            @RequestPart("transferImage") MultipartFile transferImage
    ) {
        try {
            if (req == null) return ResponseEntity.badRequest().body("신청 정보가 비어있습니다.");
            if (transferImage == null || transferImage.isEmpty()) {
                return ResponseEntity.badRequest().body("송금(입금) 이미지 파일을 업로드해주세요.");
            }

            String ct = transferImage.getContentType();
            if (ct == null || !ct.toLowerCase().startsWith("image/")) {
                return ResponseEntity.badRequest().body("이미지 파일만 업로드할 수 있습니다.");
            }

            // ✅ null/blank 방어
            String sid = (req.studentId() == null) ? "" : req.studentId().trim();
            String name = (req.name() == null) ? "" : req.name().trim();
            String phone = (req.phone() == null) ? "" : req.phone().trim();

            if (sid.isBlank() || name.isBlank() || phone.isBlank()) {
                return ResponseEntity.badRequest().body("학번/이름/전화번호는 필수입니다.");
            }

            // ✅ 카카오 연동 선행 강제
            if (!service.isKakaoLinked(sid)) {
                return ResponseEntity.status(409).body("카카오 연동이 필요합니다. 신청하기를 다시 눌러 연동을 완료해주세요.");
            }

            // ✅ 신청 처리 (승인 시점에 확인코드 발급)
            service.applyWithTransferImage(
                    sid, name, phone, req.lockerNumber(),
                    transferImage.getBytes(), ct, transferImage.getOriginalFilename()
            );

            // ✅ 그리드 갱신
            sse.broadcast("changed");

            // ✅ 코드 반환 X (승인 시점에 카톡으로 발급)
            return ResponseEntity.ok(Map.of("ok", true));

        } catch (Exception e) {
            e.printStackTrace();
            String msg = (e.getMessage() == null) ? e.getClass().getName() : e.getMessage();
            return ResponseEntity.status(500).body(": " + msg);
        }
    }

    @GetMapping("/api/public/my-status")
    public LockerService.MyStatusDto myStatus(
            @RequestParam String studentId,
            @RequestParam String code
    ) {
        return service.getMyStatus(studentId.trim(), code.trim());
    }

    // -----------------------
    // Admin
    // -----------------------

    @GetMapping("/api/admin/pending")
    public List<LockerService.PendingDto> pending() {
        return service.getPendingList();
    }

    /**
     * ✅ 관리자: 송금 이미지 확인
     */
    @GetMapping("/api/admin/pending/{applicationId}/transfer-image")
    public ResponseEntity<byte[]> transferImage(@PathVariable long applicationId) {
        LockerService.TransferImageDto dto = service.getTransferImage(applicationId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dto.contentType()));
        headers.setCacheControl("no-store");
        headers.set("Content-Disposition",
                "inline; filename=\"" + (dto.filename() == null ? "transfer-image" : dto.filename().replace("\"", "")) + "\"");

        return ResponseEntity.ok().headers(headers).body(dto.bytes());
    }

    @PostMapping("/api/admin/approve/{applicationId}")
    public ResponseEntity<?> approve(@PathVariable long applicationId) {
        LockerService.ApproveResultDto result = service.approve(applicationId);
        sse.broadcast("changed");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/reject/{applicationId}")
    public ResponseEntity<?> reject(@PathVariable long applicationId) {
        service.reject(applicationId);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/clear/{lockerNumber}")
    public ResponseEntity<?> clear(@PathVariable int lockerNumber) {
        service.clearApprovedLocker(lockerNumber);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/reset")
    public ResponseEntity<?> reset() {
        service.resetAll();
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    public record AdminAssignReq(String studentId, String name, String phone) {}
    public record AdminAssignRes(String lookupCode) {}

    @PostMapping("/api/admin/assign/{lockerNumber}")
    public ResponseEntity<?> adminAssign(
            @PathVariable int lockerNumber,
            @RequestBody AdminAssignReq req
    ) {
        String code = service.adminAssignApproved(
                req.studentId().trim(),
                req.name().trim(),
                req.phone().trim(),
                lockerNumber
        );
        sse.broadcast("changed");
        return ResponseEntity.ok(new AdminAssignRes(code));
    }

    // -----------------------
    // Public: My Locker (계정 기반)
    // -----------------------

    public record StudentLoginReq(String studentId, String password) {}

    @PostMapping("/api/public/student/login")
    public LockerService.StudentLoginDto studentLogin(@RequestBody StudentLoginReq req) {
        return service.studentLogin(req.studentId().trim(), req.password().trim());
    }

    public record StudentChangePwReq(String studentId, String currentPassword, String newPassword) {}

    @PostMapping("/api/public/student/change-password")
    public ResponseEntity<?> changeStudentPassword(@RequestBody StudentChangePwReq req) {
        service.changeStudentPassword(
                req.studentId().trim(),
                req.currentPassword().trim(),
                req.newPassword().trim()
        );
        return ResponseEntity.ok().build();
    }

    public record MyLockerByAccountReq(String studentId, String password) {}

    @PostMapping("/api/public/my-locker/by-account")
    public LockerService.MyLockerDto myLockerByAccount(@RequestBody MyLockerByAccountReq req) {
        return service.getMyLockerByAccount(req.studentId().trim(), req.password().trim());
    }

    @GetMapping("/api/public/my-locker")
    public LockerService.MyLockerDto myLocker(
            @RequestParam String studentId,
            @RequestParam String code
    ) {
        return service.getMyLocker(studentId.trim(), code.trim());
    }

    public record SaveMemoReq(String studentId, String code, String memo) {}
    public record SaveMemoByAccountReq(String studentId, String password, String memo) {}

    @PostMapping("/api/public/my-locker/memo")
    public ResponseEntity<?> saveMemo(@RequestBody SaveMemoReq req) {
        service.saveMyMemo(
                req.studentId().trim(),
                req.code().trim(),
                req.memo()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/public/my-locker/memo/by-account")
    public ResponseEntity<?> saveMemoByAccount(@RequestBody SaveMemoByAccountReq req) {
        service.saveMyMemoByAccount(
                req.studentId().trim(),
                req.password().trim(),
                req.memo()
        );
        return ResponseEntity.ok().build();
    }

    public record EmptyReq(String studentId, String code) {}
    public record EmptyByAccountReq(String studentId, String password) {}

    @PostMapping("/api/public/my-locker/empty")
    public ResponseEntity<?> empty(@RequestBody EmptyReq req) {
        service.emptyMyLocker(req.studentId().trim(), req.code().trim());
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/public/my-locker/empty/by-account")
    public ResponseEntity<?> emptyByAccount(@RequestBody EmptyByAccountReq req) {
        service.emptyMyLockerByAccount(req.studentId().trim(), req.password().trim());
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }
}
