package com.cse.locker.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    /** SSE 연결 타임아웃: 30분 (무제한 대신 제한) */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    /** 최대 동시 SSE 연결 수 (DoS 방지) */
    private static final int MAX_EMITTERS = 50;

    // 현재 연결된 SSE 클라이언트들을 보관
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    @GetMapping("/sse/admin")
    public SseEmitter subscribe() {
        // 최대 연결 수 초과 시 오래된 연결 정리
        if (emitters.size() >= MAX_EMITTERS) {
            log.warn("[SSE] Max emitter count reached ({}), refusing new connection", MAX_EMITTERS);
            SseEmitter rejected = new SseEmitter(0L);
            rejected.completeWithError(new RuntimeException("Too many SSE connections"));
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        // 연결 종료/타임아웃/에러 시 목록에서 제거
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 최초 연결 확인용 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("ok"));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(String eventName) {
        // dead emitter 수집 후 일괄 제거 (ConcurrentModificationException 방지)
        Set<SseEmitter> dead = ConcurrentHashMap.newKeySet();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data("update"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
    }
}
