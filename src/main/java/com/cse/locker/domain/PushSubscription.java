package com.cse.locker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "push_subscriptions")
@Getter @Setter @NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 브라우저 Push 엔드포인트 URL (기기별 고유값) */
    @Column(unique = true, nullable = false, length = 1024)
    private String endpoint;

    /** 사용자 공개키 (base64url) */
    @Column(nullable = false, length = 512)
    private String p256dh;

    /** 인증 비밀 (base64url) */
    @Column(nullable = false, length = 256)
    private String auth;

    /**
     * 구독과 연결된 학번 (nullable).
     * 학생이 학번을 입력 후 구독하면 저장되어 개인 알림에 사용된다.
     */
    @Column(length = 64)
    private String studentId;

    /** 구독 최초 생성 시각 */
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
