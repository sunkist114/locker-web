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

    @GetMapping("/api/public/lockers")
    public List<LockerService.LockerDto> lockers() {
        return service.getLockerGrid();
    }

    public record ApplyReq(String studentId, String name, String phone, int lockerNumber) {}
    public record ApplyRes(String lookupCode) {}

    @PostMapping("/api/public/apply")
    public ResponseEntity<?> apply(@RequestBody ApplyReq req) {
        String code = service.apply(req.studentId().trim(), req.name().trim(), req.phone().trim(), req.lockerNumber());
        sse.broadcast("changed");
        return ResponseEntity.ok(new ApplyRes(code));
    }

    // ✅ code 필수
    @GetMapping("/api/public/my-status")
    public LockerService.MyStatusDto myStatus(@RequestParam String studentId, @RequestParam String code) {
        return service.getMyStatus(studentId.trim(), code.trim());
    }

    // -----------------------
    // Admin
    // -----------------------

    @GetMapping("/api/admin/pending")
    public List<LockerService.PendingDto> pending() {
        return service.getPendingList();
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

    // ✅ 관리자: 빈 사물함에 사용자 직접 지정 (즉시 APPROVED) + 확인코드 자동 생성
    public record AdminAssignReq(String studentId, String name, String phone) {}
    public record AdminAssignRes(String lookupCode) {}

    @PostMapping("/api/admin/assign/{lockerNumber}")
    public ResponseEntity<?> adminAssign(@PathVariable int lockerNumber, @RequestBody AdminAssignReq req) {
        String code = service.adminAssignApproved(req.studentId().trim(), req.name().trim(), req.phone().trim(), lockerNumber);
        sse.broadcast("changed");
        return ResponseEntity.ok(new AdminAssignRes(code));
    }

    // ===== Public: My Locker Page =====
    @GetMapping("/api/public/my-locker")
    public LockerService.MyLockerDto myLocker(@RequestParam String studentId, @RequestParam String code) {
        return service.getMyLocker(studentId.trim(), code.trim());
    }

    public record SaveMemoReq(String studentId, String code, String memo) {}

    @PostMapping("/api/public/my-locker/memo")
    public ResponseEntity<?> saveMemo(@RequestBody SaveMemoReq req) {
        service.saveMyMemo(req.studentId().trim(), req.code().trim(), req.memo());
        return ResponseEntity.ok().build();
    }

    public record EmptyReq(String studentId, String code) {}

    @PostMapping("/api/public/my-locker/empty")
    public ResponseEntity<?> empty(@RequestBody EmptyReq req) {
        service.emptyMyLocker(req.studentId().trim(), req.code().trim());
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

}
