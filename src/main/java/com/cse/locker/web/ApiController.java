package com.cse.locker.web;

import com.cse.locker.service.LockerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    public record ApplyReq(String studentId, String name, String phone, int lockerNumber) {}
    public record ApplyRes(String lookupCode) {}

    /**
     * ✅ 학생 신청 + 송금(보증금) 이미지 업로드 (최종)
     * - consumes: multipart/form-data
     * - data(JSON) + transferImage(file)
     */
    @PostMapping(value = "/api/public/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> apply(
            @RequestPart("data") ApplyReq req,
            @RequestPart("transferImage") MultipartFile transferImage
    ) throws Exception {

        if (req == null) {
            return ResponseEntity.badRequest().body("신청 정보가 비어있습니다.");
        }

        if (transferImage == null || transferImage.isEmpty()) {
            return ResponseEntity.badRequest().body("송금(입금) 이미지 파일을 업로드해주세요.");
        }

        String ct = transferImage.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body("이미지 파일만 업로드할 수 있습니다.");
        }

        String code = service.applyWithTransferImage(
                req.studentId().trim(),
                req.name().trim(),
                req.phone().trim(),
                req.lockerNumber(),
                transferImage.getBytes(),
                ct,
                transferImage.getOriginalFilename()
        );

        sse.broadcast("changed");
        return ResponseEntity.ok(new ApplyRes(code));
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
     * ✅ 관리자: 송금 이미지 확인 (관리자 페이지 모달에서 <img>로 띄움)
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
        service.approve(applicationId);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
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
    // Public: My Locker
    // -----------------------

    // ✅ 학생 계정 로그인(자동 생성 계정)
    public record StudentLoginReq(String studentId, String password) {}

    @PostMapping("/api/public/student/login")
    public LockerService.StudentLoginDto studentLogin(@RequestBody StudentLoginReq req) {
        return service.studentLogin(req.studentId().trim(), req.password().trim());
    }

    // ✅ 학생 계정 비밀번호 변경
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

    // ✅ 학생 계정 기반 "내 사물함 정보" 조회
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
