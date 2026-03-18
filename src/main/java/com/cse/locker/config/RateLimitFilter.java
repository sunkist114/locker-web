package com.cse.locker.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인메모리 Rate Limiter.
 * 확인코드 브루트포스 방지를 위해 IP별 요청 횟수 제한.
 *
 * 대상: /api/public/my-status, /api/public/student/login, /api/public/student/change-password
 * 제한: IP당 1분에 최대 10회 (실패 포함)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MS = 60_000L; // 1분

    /** IP별 (요청 횟수, 윈도우 시작 시각) */
    private final Map<String, RateWindow> ipMap = new ConcurrentHashMap<>();

    /** 주기적 정리: 마지막 정리 시각 */
    private volatile long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 5 * 60_000L; // 5분마다

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        String path = request.getRequestURI();

        // Rate limit 대상 경로만 필터링
        if (!isRateLimited(path)) {
            chain.doFilter(req, res);
            return;
        }

        String ip = resolveClientIp(request);
        cleanupIfNeeded();

        RateWindow window = ipMap.compute(ip, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.startTime > WINDOW_MS) {
                return new RateWindow(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > MAX_REQUESTS_PER_WINDOW) {
            HttpServletResponse response = (HttpServletResponse) res;
            response.setStatus(429);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"요청이 너무 많습니다. 1분 후 다시 시도해주세요.\"}");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isRateLimited(String path) {
        return path.startsWith("/api/public/my-status")
                || path.startsWith("/api/public/student/login")
                || path.startsWith("/api/public/student/change-password")
                || path.startsWith("/api/public/my-locker");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** 오래된 윈도우 정리 (메모리 누수 방지) */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;
        ipMap.entrySet().removeIf(e -> now - e.getValue().startTime > WINDOW_MS * 2);
    }

    private static class RateWindow {
        final long startTime;
        final AtomicInteger count;

        RateWindow(long startTime, AtomicInteger count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
