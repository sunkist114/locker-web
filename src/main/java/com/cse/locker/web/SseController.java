package com.cse.locker.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    // 현재 연결된 SSE 클라이언트들을 보관
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    @GetMapping("/sse/admin")
    public SseEmitter subscribe() {
        // timeout 0L = 연결 제한 없이 유지
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        // 연결 종료/타임아웃/에러 시 목록에서 제거
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // 최초 연결 확인용 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("ok"));
        } catch (IOException ignored) {
        }

        return emitter;
    }

    public void broadcast(String eventName) {
        // 모든 연결된 클라이언트에게 이벤트 전파
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data("update"));
            } catch (IOException e) {
                // 전송 실패한 연결은 제거
                emitters.remove(emitter);
            }
        }
    }
}
