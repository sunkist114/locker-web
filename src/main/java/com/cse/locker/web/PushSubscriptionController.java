package com.cse.locker.web;

import com.cse.locker.domain.PushSubscription;
import com.cse.locker.repo.PushSubscriptionRepository;
import com.cse.locker.service.WebPushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository repo;
    private final WebPushService webPushService;

    public PushSubscriptionController(PushSubscriptionRepository repo, WebPushService webPushService) {
        this.repo = repo;
        this.webPushService = webPushService;
    }

    /** 클라이언트가 구독 전에 VAPID 공개키를 가져가는 엔드포인트 */
    @GetMapping("/vapid-public-key")
    public Map<String, String> vapidPublicKey() {
        return Map.of("publicKey", webPushService.getVapidPublicKey());
    }

    /** 브라우저 Push 구독 정보 저장 (upsert by endpoint) */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscribeReq req) {
        if (req.endpoint() == null || req.endpoint().isBlank()) {
            return ResponseEntity.badRequest().body("endpoint required");
        }
        if (req.keys() == null) {
            return ResponseEntity.badRequest().body("keys required");
        }

        PushSubscription sub = repo.findByEndpoint(req.endpoint())
                .orElse(new PushSubscription());

        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.keys().p256dh());
        sub.setAuth(req.keys().auth());

        if (req.studentId() != null && !req.studentId().isBlank()) {
            sub.setStudentId(req.studentId().trim());
        }

        repo.save(sub);
        return ResponseEntity.ok().build();
    }

    // DTO: 브라우저의 PushSubscription.toJSON() 구조와 매핑
    public record SubscribeReq(String endpoint, Keys keys, String studentId) {
        public record Keys(String p256dh, String auth) {}
    }
}
