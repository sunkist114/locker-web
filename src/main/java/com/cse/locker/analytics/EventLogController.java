package com.cse.locker.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogRepository eventLogRepo;

    /** 프론트에서 이벤트를 전송하는 공개 API (인증 불필요) */
    @PostMapping("/api/public/event")
    public ResponseEntity<Void> logEvent(@RequestBody EventLogRequest req) {
        if (req.eventName() == null || req.eventName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String eventName = truncate(req.eventName().strip(), 64);
        String pageName  = req.pageName()  != null ? truncate(req.pageName().strip(),  64) : null;
        String studentId = req.studentId() != null ? truncate(req.studentId().strip(), 64) : null;
        String meta      = req.meta();

        eventLogRepo.save(EventLog.builder()
                .eventName(eventName)
                .pageName(pageName)
                .studentId(studentId)
                .occurredAt(Instant.now())
                .meta(meta)
                .build());

        return ResponseEntity.ok().build();
    }

    /** 관리자 전용: 최근 200건 조회 */
    @GetMapping("/api/admin/events")
    public List<EventLog> getEvents() {
        return eventLogRepo.findTop200ByOrderByOccurredAtDesc();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
