package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LockerService {

    private final LockerRepository lockerRepo;
    private final ApplicationRepository appRepo;

    public LockerService(LockerRepository lockerRepo, ApplicationRepository appRepo) {
        this.lockerRepo = lockerRepo;
        this.appRepo = appRepo;
    }

    public record LockerDto(int lockerNumber, String status, String studentId) {}
    public record PendingDto(long id, String studentId, String name, String phone, int lockerNumber) {}

    @PostConstruct
    @Transactional
    public void initLockers() {
        for (int i = 1; i <= 50; i++) {
            final int n = i;
            lockerRepo.findById(n).orElseGet(() -> lockerRepo.save(new Locker(n)));
        }
    }

    // -----------------------
    // Public
    // -----------------------

    @Transactional(readOnly = true)
    public List<LockerDto> getLockerGrid() {
        List<LockerDto> out = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            final int n = i;
            Locker l = lockerRepo.findById(n)
                    .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + n));

            out.add(new LockerDto(
                    l.getLockerNumber(),
                    l.getState().name(),
                    l.getReservedStudentId()
            ));
        }
        return out;
    }

    @Transactional
    public void apply(String studentId, String name, String phone, int lockerNumber) {
        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        if (locker.getState() != Locker.State.AVAILABLE) {
            throw new IllegalStateException("이미 예약/사용 중인 사물함입니다.");
        }

        // 신청(PENDING) 생성
        Application app = new Application(studentId, name, phone, lockerNumber, Application.Status.PENDING);
        appRepo.save(app);

        // 사물함 RESERVED
        locker.setState(Locker.State.RESERVED);
        locker.setReservedStudentId(studentId);
        lockerRepo.save(locker);
    }

    // -----------------------
    // Admin
    // -----------------------

    @Transactional(readOnly = true)
    public List<PendingDto> getPending() {
        List<Application> pending = appRepo.findByStatus(Application.Status.PENDING);
        List<PendingDto> out = new ArrayList<>();
        for (Application a : pending) {
            out.add(new PendingDto(
                    a.getId(),
                    a.getStudentId(),
                    a.getName(),
                    a.getPhone(),
                    a.getLockerNumber()
            ));
        }
        return out;
    }

    @Transactional
    public void approve(long id) {
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("신청 없음: " + id));

        if (app.getStatus() != Application.Status.PENDING) {
            throw new IllegalStateException("대기 상태가 아닙니다.");
        }

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        app.setStatus(Application.Status.APPROVED);
        appRepo.save(app);

        locker.setState(Locker.State.APPROVED);
        locker.setReservedStudentId(app.getStudentId());
        lockerRepo.save(locker);
    }

    @Transactional
    public void reject(long id) {
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("신청 없음: " + id));

        int lockerNumber = app.getLockerNumber();
        String studentId = app.getStudentId();

        // ✅ 거절은 기록 삭제
        appRepo.delete(app);

        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        // RESERVED 상태이고 동일 학생이면 해제
        if (locker.getState() == Locker.State.RESERVED &&
                studentId.equals(locker.getReservedStudentId())) {
            locker.setState(Locker.State.AVAILABLE);
            locker.setReservedStudentId(null);
            lockerRepo.save(locker);
        }
    }

    @Transactional
    public void clearApprovedLocker(int lockerNumber) {
        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        if (locker.getState() != Locker.State.APPROVED) {
            throw new IllegalStateException("승인된 사물함만 비울 수 있습니다.");
        }

        // ✅ (핵심) 해당 사물함 신청/승인 기록 삭제 -> 학생도 더 이상 '사용중'이 아님
        appRepo.deleteByLockerNumber(lockerNumber);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }

    @Transactional
    public void resetAll() {
        // 신청 데이터 전부 삭제
        appRepo.deleteAll();

        // 사물함 1~50 전부 AVAILABLE
        for (int i = 1; i <= 50; i++) {
            final int n = i;
            Locker locker = lockerRepo.findById(n).orElseGet(() -> new Locker(n));
            locker.setState(Locker.State.AVAILABLE);
            locker.setReservedStudentId(null);
            lockerRepo.save(locker);
        }
    }

    // 학생: 내 신청 상태 조회 응답 DTO
    public record MyStatusDto(
            String studentId,
            String status,        // NONE / PENDING / APPROVED
            Integer lockerNumber, // 없으면 null
            String message
    ) {}

    @Transactional(readOnly = true)
    public MyStatusDto getMyStatus(String studentId) {
        // 신청 기록이 아예 없으면 NONE
        var opt = appRepo.findTopByStudentIdOrderByIdDesc(studentId);
        if (opt.isEmpty()) {
            return new MyStatusDto(studentId, "NONE", null, "신청/사용 중인 사물함이 없습니다.");
        }

        Application app = opt.get();
        String s = app.getStatus().name(); // PENDING or APPROVED

        // 혹시 DB에 기록은 있는데 사물함 상태가 AVAILABLE로 풀린 경우(비우기/초기화 직후) 방어
        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElse(null);
        if (locker == null || locker.getState() == Locker.State.AVAILABLE) {
            return new MyStatusDto(studentId, "NONE", null, "현재 사용 중인 사물함이 없습니다.");
        }

        if (app.getStatus() == Application.Status.PENDING) {
            return new MyStatusDto(studentId, "PENDING", app.getLockerNumber(), "신청이 접수되었습니다. 관리자 승인을 기다려주세요.");
        } else {
            return new MyStatusDto(studentId, "APPROVED", app.getLockerNumber(), "승인되어 사용 중입니다.");
        }
    }
}
