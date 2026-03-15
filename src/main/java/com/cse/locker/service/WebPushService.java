package com.cse.locker.service;

import com.cse.locker.domain.PushSubscription;
import com.cse.locker.repo.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    static {
        // BouncyCastle 보안 공급자 등록 (VAPID 서명에 필요)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushSubscriptionRepository repo;
    private final ObjectMapper om;

    @Value("${vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${vapid.subject:mailto:admin@example.com}")
    private String vapidSubject;

    private PushService pushService;

    public WebPushService(PushSubscriptionRepository repo, ObjectMapper om) {
        this.repo = repo;
        this.om = om;
    }

    @PostConstruct
    public void init() {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()
                || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            log.warn("[WebPush] VAPID 키가 설정되지 않았습니다. Web Push 비활성화.");
            return;
        }
        try {
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            log.info("[WebPush] PushService 초기화 완료.");
        } catch (Exception e) {
            log.error("[WebPush] PushService 초기화 실패: {}", e.getMessage());
        }
    }

    /** 특정 학번에 연결된 모든 구독으로 알림 전송 */
    public void sendToStudent(String studentId, String title, String body) {
        if (pushService == null || studentId == null || studentId.isBlank()) return;
        List<PushSubscription> subs = repo.findByStudentId(studentId.trim());
        for (PushSubscription sub : subs) {
            send(sub, title, body);
        }
    }

    /** 전체 구독자에게 알림 전송 */
    public void sendToAll(String title, String body) {
        if (pushService == null) return;
        for (PushSubscription sub : repo.findAll()) {
            send(sub, title, body);
        }
    }

    private void send(PushSubscription sub, String title, String body) {
        try {
            String payload = om.writeValueAsString(Map.of("title", title, "body", body));
            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8)
            );
            var response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 410 || status == 404) {
                // 구독이 만료됨 → DB에서 삭제
                repo.deleteByEndpoint(sub.getEndpoint());
                log.info("[WebPush] 만료된 구독 삭제: {}", sub.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("[WebPush] 전송 실패 (endpoint={}): {}", sub.getEndpoint(), e.getMessage());
        }
    }

    public String getVapidPublicKey() {
        return vapidPublicKey == null ? "" : vapidPublicKey;
    }
}
