package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import com.cse.locker.repo.StudentAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final ApplicationRepository appRepo;
    private final LockerRepository lockerRepo;
    private final StudentAccountRepository studentAccountRepo;
    private final KakaoTalkService kakaoTalkService;
    private final WebPushService webPushService;

    public ExpiryScheduler(ApplicationRepository appRepo,
                           LockerRepository lockerRepo,
                           StudentAccountRepository studentAccountRepo,
                           KakaoTalkService kakaoTalkService,
                           WebPushService webPushService) {
        this.appRepo = appRepo;
        this.lockerRepo = lockerRepo;
        this.studentAccountRepo = studentAccountRepo;
        this.kakaoTalkService = kakaoTalkService;
        this.webPushService = webPushService;
    }

    // ✅ 테스트할 땐 아래로 잠깐 바꾸면 바로 확인 가능
    // @Scheduled(cron = "0 */1 * * * *", zone = "Asia/Seoul")

    // 매일 09:00 KST
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime nowZdt = ZonedDateTime.now(zone);
        Instant now = nowZdt.toInstant();
        LocalDate today = nowZdt.toLocalDate();

        log.info("[ExpiryScheduler] run at {}", nowZdt);

        List<Application> approvedList = appRepo.findByStatus(Application.Status.APPROVED);
        if (approvedList.isEmpty()) {
            log.info("[ExpiryScheduler] no APPROVED applications");
            return;
        }

        for (Application a : approvedList) {
            try {
                if (a.getExpiresAt() == null) {
                    log.warn("[ExpiryScheduler] expiresAt is null. studentId={}, locker={}",
                            a.getStudentId(), a.getLockerNumber());
                    continue;
                }

                LocalDate expiresDate = a.getExpiresAt().atZone(zone).toLocalDate();
                long daysLeft = ChronoUnit.DAYS.between(today, expiresDate);

                log.info("[ExpiryScheduler] check studentId={}, locker={}, expiresAt={}, expiresDate={}, today={}, daysLeft={}, notifiedD7={}, notifiedD1={}",
                        a.getStudentId(),
                        a.getLockerNumber(),
                        a.getExpiresAt(),
                        expiresDate,
                        today,
                        daysLeft,
                        a.isNotifiedD7(),
                        a.isNotifiedD1()
                );

                // =========================
                // 만료(현재 시간이 expiresAt 이후면)
                // =========================
                if (now.isAfter(a.getExpiresAt())) {
                    a.setStatus(Application.Status.EXPIRED);
                    a.setExpiredAt(now);

                    // ✅ 너가 작성한 텍스트 그대로 유지 (절대 수정 X)
                    try {
                        kakaoTalkService.sendToStudent(a.getStudentId(),
                                "[사물함 만료 안내]\n",
                                ("사물함 %d번의 사용 기간이 만료되었습니다.\n" +
                                        "보증금 반환 정보는 https://naver.me/xTsKeZgS 여기서 입력 부탁드립니다.")
                                        .formatted(a.getLockerNumber()));
                    } catch (Exception e) {
                        log.warn("[ExpiryScheduler] kakao send failed(EXPIRED) studentId={}, locker={} err={}",
                                a.getStudentId(), a.getLockerNumber(), e.toString());
                    }

                    // ✅ Web Push 알림 (만료)
                    try {
                        webPushService.sendToStudent(a.getStudentId(), "사물함 만료 안내",
                                a.getLockerNumber() + "번 사물함의 사용 기간이 만료되었습니다.");
                    } catch (Exception ignore) {}

                    // 사물함 비우기 + 학생계정 삭제
                    Locker locker = lockerRepo.findById(a.getLockerNumber()).orElse(null);
                    if (locker != null && locker.getState() == Locker.State.APPROVED) {
                        locker.setState(Locker.State.AVAILABLE);
                        locker.setReservedStudentId(null);
                        lockerRepo.save(locker);
                    }

                    studentAccountRepo.deleteById(a.getStudentId());

                    // ✅ 상태/시간 변경을 DB에 확실히 반영
                    appRepo.save(a);

                    // ✅ 만료 처리했으면 아래 D-7/D-1은 볼 필요 없음
                    continue;
                }

                boolean changed = false;

                // =========================
                // D-7
                // =========================
                if (daysLeft == 7 && !a.isNotifiedD7()) {
                    // ✅ 너가 작성한 텍스트 그대로 유지 (절대 수정 X)
                    try {
                        kakaoTalkService.sendToStudent(a.getStudentId(),
                                "[사물함 만료 예정 안내]\n",
                                "사물함 만료 D-7\n\n" +
                                        "사물함 %d번 사용 만료까지 7일 남았습니다."
                                                .formatted(a.getLockerNumber()));
                    } catch (Exception e) {
                        log.warn("[ExpiryScheduler] kakao send failed(D-7) studentId={}, locker={} err={}",
                                a.getStudentId(), a.getLockerNumber(), e.toString());
                    }

                    // ✅ Web Push 알림 (D-7)
                    try {
                        webPushService.sendToStudent(a.getStudentId(), "사물함 만료 D-7",
                                a.getLockerNumber() + "번 사물함 사용 만료까지 7일 남았습니다.");
                    } catch (Exception ignore) {}

                    a.setNotifiedD7(true);
                    changed = true;
                }

                // =========================
                // D-1
                // =========================
                if (daysLeft == 1 && !a.isNotifiedD1()) {
                    // ✅ 너가 작성한 텍스트 그대로 유지 (절대 수정 X)
                    try {
                        kakaoTalkService.sendToStudent(a.getStudentId(),
                                "[사물함 만료 예정 안내]\n",
                                "사물함 만료 D-1\n\n" +
                                        "사물함 %d번 사용 만료까지 1일 남았습니다."
                                                .formatted(a.getLockerNumber()));
                    } catch (Exception e) {
                        log.warn("[ExpiryScheduler] kakao send failed(D-1) studentId={}, locker={} err={}",
                                a.getStudentId(), a.getLockerNumber(), e.toString());
                    }

                    // ✅ Web Push 알림 (D-1)
                    try {
                        webPushService.sendToStudent(a.getStudentId(), "사물함 만료 D-1",
                                a.getLockerNumber() + "번 사물함 사용 만료까지 1일 남았습니다.");
                    } catch (Exception ignore) {}

                    a.setNotifiedD1(true);
                    changed = true;
                }

                // ✅ 플래그가 바뀌었으면 저장
                if (changed) {
                    appRepo.save(a);
                }

            } catch (Exception e) {
                // ✅ 한 명에서 오류가 나도 전체 스케줄러가 죽지 않게
                log.error("[ExpiryScheduler] error for studentId={}, locker={}",
                        a.getStudentId(), a.getLockerNumber(), e);
            }
        }
    }
}
