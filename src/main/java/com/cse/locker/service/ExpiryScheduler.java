package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import com.cse.locker.repo.StudentAccountRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ExpiryScheduler {

    private final ApplicationRepository appRepo;
    private final LockerRepository lockerRepo;
    private final StudentAccountRepository studentAccountRepo;
    private final KakaoTalkService kakaoTalkService;

    public ExpiryScheduler(ApplicationRepository appRepo,
                           LockerRepository lockerRepo,
                           StudentAccountRepository studentAccountRepo,
                           KakaoTalkService kakaoTalkService) {
        this.appRepo = appRepo;
        this.lockerRepo = lockerRepo;
        this.studentAccountRepo = studentAccountRepo;
        this.kakaoTalkService = kakaoTalkService;
    }

    // 매일 09:00 KST
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        List<Application> approvedList = appRepo.findByStatus(Application.Status.APPROVED);
        if (approvedList.isEmpty()) return;

        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);

        for (Application a : approvedList) {
            if (a.getExpiresAt() == null) continue;

            LocalDate expiresDate = a.getExpiresAt().atZone(zone).toLocalDate();
            long daysLeft = ChronoUnit.DAYS.between(today, expiresDate);

            // D-7
            if (daysLeft == 7 && !a.isNotifiedD7()) {
                kakaoTalkService.sendToStudent(a.getStudentId(),
                        "사물함 만료 D-7",
                        "사물함 %d번 사용 만료까지 7일 남았습니다.".formatted(a.getLockerNumber()));
                a.setNotifiedD7(true);
            }

            // D-1
            if (daysLeft == 1 && !a.isNotifiedD1()) {
                kakaoTalkService.sendToStudent(a.getStudentId(),
                        "사물함 만료 D-1",
                        "사물함 %d번 사용 만료까지 1일 남았습니다.".formatted(a.getLockerNumber()));
                a.setNotifiedD1(true);
            }

            // 만료(오늘이 만료일 이후면)
            Instant now = Instant.now();
            if (now.isAfter(a.getExpiresAt())) {
                a.setStatus(Application.Status.EXPIRED);
                a.setExpiredAt(now);

                kakaoTalkService.sendToStudent(a.getStudentId(),
                        "사물함 사용 만료",
                        "사물함 %d번 사용기간이 만료되었습니다.".formatted(a.getLockerNumber()));

                // 사물함 비우기 + 학생계정 삭제
                Locker locker = lockerRepo.findById(a.getLockerNumber()).orElse(null);
                if (locker != null && locker.getState() == Locker.State.APPROVED) {
                    locker.setState(Locker.State.AVAILABLE);
                    locker.setReservedStudentId(null);
                    lockerRepo.save(locker);
                }
                studentAccountRepo.deleteById(a.getStudentId());
            }
        }
    }
}
