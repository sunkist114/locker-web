package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class LockerService {

    private final LockerRepository lockerRepo;
    private final ApplicationRepository appRepo;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom random = new SecureRandom();

    public LockerService(LockerRepository lockerRepo, ApplicationRepository appRepo, PasswordEncoder passwordEncoder) {
        this.lockerRepo = lockerRepo;
        this.appRepo = appRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public record LockerDto(int lockerNumber, String state, String studentId) {}
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

            String status = l.getState().name();
            if ("RESERVED".equals(status)) status = "PENDING";

            out.add(new LockerDto(
                    l.getLockerNumber(),
                    status,
                    l.getReservedStudentId()
            ));
        }
        return out;
    }

    /** ✅ 6자리 확인코드 생성 */
    private String generateLookupCode() {
        int v = random.nextInt(900000) + 100000; // 100000~999999
        return String.valueOf(v);
    }

    /** ✅ 최신 신청 1개 기준으로 code 검증 */
    private Application requireValidLookup(String studentId, String code) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("학번이 비었습니다.");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("확인코드를 입력해주세요.");
        }

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("학번 또는 확인코드가 올바르지 않습니다."));

        if (app.getLookupCodeHash() == null || app.getLookupCodeHash().isBlank()) {
            // 예전 데이터(코드 없던 시절) 방어
            throw new IllegalStateException("확인코드가 설정되어 있지 않습니다. 다시 신청해주세요.");
        }

        boolean ok = passwordEncoder.matches(code.trim(), app.getLookupCodeHash());
        if (!ok) {
            throw new IllegalStateException("학번 또는 확인코드가 올바르지 않습니다.");
        }
        return app;
    }

    /** ✅ 같은 학번이 이미 PENDING/APPROVED면 중복 신청 방지 */
    private void preventDuplicateApply(String studentId) {
        var opt = appRepo.findTopByStudentIdOrderByIdDesc(studentId);
        if (opt.isEmpty()) return;

        Application last = opt.get();
        // 사물함이 AVAILABLE로 풀렸으면 과거 기록이므로 허용(방어 로직)
        Locker locker = lockerRepo.findById(last.getLockerNumber()).orElse(null);
        boolean stillUsing = locker != null && locker.getState() != Locker.State.AVAILABLE;

        if (stillUsing && (last.getStatus() == Application.Status.PENDING || last.getStatus() == Application.Status.APPROVED)) {
            throw new IllegalStateException("이미 신청 또는 사용 중인 사물함이 있습니다. (중복 신청 불가)");
        }
    }

    /** ✅ 신청: 확인코드 발급해서 리턴 */
    @Transactional
    public String apply(String studentId, String name, String phone, int lockerNumber) {
        studentId = studentId.trim();
        name = name.trim();
        phone = phone.trim();

        preventDuplicateApply(studentId);

        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        if (locker.getState() != Locker.State.AVAILABLE) {
            throw new IllegalStateException("이미 예약/사용 중인 사물함입니다.");
        }

        String code = generateLookupCode();
        String hash = passwordEncoder.encode(code);

        Application app = new Application(studentId, name, phone, lockerNumber, Application.Status.PENDING);
        app.setLookupCodeHash(hash);
        appRepo.save(app);

        locker.setState(Locker.State.RESERVED);
        locker.setReservedStudentId(studentId);
        lockerRepo.save(locker);

        return code; // ✅ 프론트에 한 번 보여줄 값
    }

    // -----------------------
    // Admin
    // -----------------------

    @Transactional(readOnly = true)
    public List<PendingDto> getPendingList() {
        List<Application> list = appRepo.findByStatus(Application.Status.PENDING);
        List<PendingDto> out = new ArrayList<>();
        for (Application a : list) {
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
    public void approve(long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("없는 신청: " + applicationId));

        if (app.getStatus() != Application.Status.PENDING) {
            throw new IllegalStateException("대기 신청만 승인할 수 있습니다.");
        }

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        if (locker.getState() != Locker.State.RESERVED) {
            throw new IllegalStateException("사물함 상태가 RESERVED가 아닙니다.");
        }

        app.setStatus(Application.Status.APPROVED);
        appRepo.save(app);

        locker.setState(Locker.State.APPROVED);
        lockerRepo.save(locker);
    }

    @Transactional
    public void reject(long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("없는 신청: " + applicationId));

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        appRepo.delete(app);

        if (locker.getState() == Locker.State.RESERVED) {
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

        appRepo.deleteByLockerNumber(lockerNumber);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }

    @Transactional
    public void resetAll() {
        appRepo.deleteAll();

        for (int i = 1; i <= 50; i++) {
            final int n = i;
            Locker locker = lockerRepo.findById(n).orElseGet(() -> new Locker(n));
            locker.setState(Locker.State.AVAILABLE);
            locker.setReservedStudentId(null);
            lockerRepo.save(locker);
        }
    }

    // -----------------------
    // Student APIs
    // -----------------------

    public record MyStatusDto(
            String studentId,
            String status,        // NONE / PENDING / APPROVED
            Integer lockerNumber, // 없으면 null
            String message
    ) {}

    public record MyLockerDto(
            String status,        // NONE / PENDING / APPROVED
            String message,
            String studentId,
            String name,
            String phone,
            Integer lockerNumber,
            String memo
    ) {}

    @Transactional(readOnly = true)
    public MyStatusDto getMyStatus(String studentId, String code) {
        Application app = requireValidLookup(studentId, code);

        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElse(null);
        if (locker == null || locker.getState() == Locker.State.AVAILABLE) {
            return new MyStatusDto(studentId, "NONE", null, "현재 사용 중인 사물함이 없습니다.");
        }

        if (app.getStatus() == Application.Status.PENDING) {
            return new MyStatusDto(studentId, "PENDING", app.getLockerNumber(), "신청이 접수되었습니다. 관리자 승인을 기다려주세요.");
        }
        return new MyStatusDto(studentId, "APPROVED", app.getLockerNumber(), "승인되어 사용 중입니다.");
    }

    @Transactional(readOnly = true)
    public MyLockerDto getMyLocker(String studentId, String code) {
        Application app = requireValidLookup(studentId, code);

        if (app.getStatus() != Application.Status.APPROVED) {
            if (app.getStatus() == Application.Status.PENDING) {
                return new MyLockerDto("PENDING", "신청이 접수되었습니다. 관리자 승인을 기다려주세요.", studentId, null, null, app.getLockerNumber(), null);
            }
            return new MyLockerDto("NONE", "현재 사용 중인 사물함이 없습니다.", studentId, null, null, null, null);
        }

        return new MyLockerDto(
                "APPROVED",
                "승인되어 사용 중입니다.",
                app.getStudentId(),
                app.getName(),
                app.getPhone(),
                app.getLockerNumber(),
                app.getMemo()
        );
    }

    @Transactional
    public void saveMyMemo(String studentId, String code, String memo) {
        Application app = requireValidLookup(studentId, code);

        if (app.getStatus() != Application.Status.APPROVED) {
            throw new IllegalStateException("승인된 사물함이 없습니다.");
        }

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        if (locker.getState() != Locker.State.APPROVED || !studentId.equals(locker.getReservedStudentId())) {
            throw new IllegalStateException("현재 사용 중인 사물함이 아닙니다.");
        }

        app.setMemo(memo == null ? "" : memo);
        appRepo.save(app);
    }

    @Transactional
    public void emptyMyLocker(String studentId, String code) {
        Application app = requireValidLookup(studentId, code);

        if (app.getStatus() != Application.Status.APPROVED) {
            throw new IllegalStateException("승인된 사물함이 없습니다.");
        }

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        if (locker.getState() != Locker.State.APPROVED || !studentId.equals(locker.getReservedStudentId())) {
            throw new IllegalStateException("현재 사용 중인 사물함이 아닙니다.");
        }

        appRepo.delete(app);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }
}
