package com.cse.locker.web;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    @GetMapping("/sse/admin")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 무제한
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // 연결 즉시 한 번 쏴서 클라이언트가 “연결됨”을 확인하게 해도 좋음
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {}

        return emitter;
    }

    public void broadcast(String eventName) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data("update"));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
