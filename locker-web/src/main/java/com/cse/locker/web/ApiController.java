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

    // ===== Public =====

    @GetMapping("/api/public/grid")
    public List<LockerService.LockerDto> grid() {
        return service.getLockerGrid();
    }

    public record ApplyReq(String studentId, String name, String phone, int lockerNumber) {}

    @PostMapping("/api/public/apply")
    public ResponseEntity<?> apply(@RequestBody ApplyReq req) {
        service.apply(req.studentId(), req.name(), req.phone(), req.lockerNumber());
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    // ===== Admin =====

    @GetMapping("/api/admin/pending")
    public List<LockerService.PendingDto> pending() {
        return service.getPending();
    }

    @PostMapping("/api/admin/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable long id) {
        service.approve(id);
        sse.broadcast("changed");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/admin/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable long id) {
        service.reject(id);
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

    @GetMapping("/api/public/my-status")
    public LockerService.MyStatusDto myStatus(@RequestParam String studentId) {
        return service.getMyStatus(studentId.trim());
    }
}
