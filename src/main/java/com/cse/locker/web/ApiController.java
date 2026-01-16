package com.cse.locker.web;

import com.cse.locker.service.LockerService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
        // 사물함 전체 상태 조회
        return service.getLockerGrid();
    }

    public record ApplyReq(String studentId, String name, String phone, int lockerNumber) {}
    public record ApplyRes(String lookupCode) {}

    @PostMapping("/api/public/apply")
    public ResponseEntity<?> apply(@RequestBody ApplyReq req) {
        // 사물함 신청 + 확인코드 발급
        String code = service.apply(
                req.studentId().trim(),
                req.name().trim(),
                req.phone().trim(),
                req.lockerNumber()
        );
        sse.broadcast("changed");
        return ResponseEntity.ok(new ApplyRes(code));
    }

    @GetMapping("/api/public/my-status")
    public LockerService.MyStatusDto myStatus(
            @RequestParam String studentId,
            @RequestParam String code
    ) {
        // 학번 + 확인코드로 현재 상태 조회
        return service.getMyStatus(studentId.trim(), code.trim());
    }

    // -----------------------
    // Admin
    // -----------------------

    @GetMapping("/api/admin/pending")
    public List<LockerService.PendingDto> pending() {
        // 대기(PENDING) 신청 목록 조회
        return service.getPendingList();
    }

    @PostMapping("/api/admin/approve/{applicationId}")
    public ResponseEntity<?> approve(@PathVariable long applicationId) {
        // 신청 승인
        service.approve(applicationId);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/reject/{applicationId}")
    public ResponseEntity<?> reject(@PathVariable long applicationId) {
        // 신청 반려
        service.reject(applicationId);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/clear/{lockerNumber}")
    public ResponseEntity<?> clear(@PathVariable int lockerNumber) {
        // 승인된 사물함 비우기
        service.clearApprovedLocker(lockerNumber);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/reset")
    public ResponseEntity<?> reset() {
        // 전체 초기화
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
        // 관리자 직접 지정 후 즉시 승인
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

    @GetMapping("/api/public/my-locker")
    public LockerService.MyLockerDto myLocker(
            @RequestParam String studentId,
            @RequestParam String code
    ) {
        // 내 사물함 정보 조회
        return service.getMyLocker(studentId.trim(), code.trim());
    }

    public record SaveMemoReq(String studentId, String code, String memo) {}

    @PostMapping("/api/public/my-locker/memo")
    public ResponseEntity<?> saveMemo(@RequestBody SaveMemoReq req) {
        // 사물함 메모 저장
        service.saveMyMemo(
                req.studentId().trim(),
                req.code().trim(),
                req.memo()
        );
        return ResponseEntity.ok().build();
    }

    public record EmptyReq(String studentId, String code) {}

    @PostMapping("/api/public/my-locker/empty")
    public ResponseEntity<?> empty(@RequestBody EmptyReq req) {
        // 사물함 반납
        service.emptyMyLocker(req.studentId().trim(), req.code().trim());
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }
}
