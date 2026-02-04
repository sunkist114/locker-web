package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.domain.StudentAccount;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import com.cse.locker.repo.StudentAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class LockerService {

    private final LockerRepository lockerRepo;
    private final ApplicationRepository appRepo;
    private final StudentAccountRepository studentRepo;
    private final PasswordEncoder passwordEncoder;
    private final KakaoTalkService kakaoTalkService;

    @Value("${app.locker.expires-at:}")
    private String expiresAtConfig;

    public LockerService(LockerRepository lockerRepo,
                         ApplicationRepository appRepo,
                         StudentAccountRepository studentRepo,
                         PasswordEncoder passwordEncoder,
                         KakaoTalkService kakaoTalkService) {
        this.lockerRepo = lockerRepo;
        this.appRepo = appRepo;
        this.studentRepo = studentRepo;
        this.passwordEncoder = passwordEncoder;
        this.kakaoTalkService = kakaoTalkService;
    }

    /* =========================
       DTO / RECORDS
       ========================= */

    public record LockerDto(int lockerNumber, String state, String studentId) {}
    public record PendingDto(long id, String studentId, String name, String phone, int lockerNumber) {}
    public record TransferImageDto(byte[] bytes, String contentType, String filename) {}

    public record MyStatusDto(
            String studentId,
            String status,
            Integer lockerNumber,
            String message
    ) {}

    public record MyLockerDto(
            String status,
            String message,
            String studentId,
            String name,
            String phone,
            Integer lockerNumber,
            String memo,
            Instant expiresAt
    ) {}

    public record StudentLoginDto(
            String studentId,
            String status,
            Integer lockerNumber,
            String message
    ) {}

    /* =========================
       Utils
       ========================= */

    private Instant resolveExpiresAtOrDefault(Instant baseTime) {
        if (expiresAtConfig != null && !expiresAtConfig.isBlank()) {
            return ZonedDateTime.parse(expiresAtConfig).toInstant();
        }
        Instant t = (baseTime == null) ? Instant.now() : baseTime;
        return t.plusSeconds(365L * 24 * 60 * 60); // 1년
    }

    private StudentAccount auth(String studentId, String password) {
        StudentAccount acc = studentRepo.findById(studentId)
                .orElseThrow(() -> new IllegalStateException("계정이 존재하지 않습니다."));
        if (!passwordEncoder.matches(password, acc.getPasswordHash())) {
            throw new IllegalStateException("비밀번호가 올바르지 않습니다.");
        }
        return acc;
    }

    /* =========================
       Kakao helpers
       ========================= */

    @Transactional(readOnly = true)
    public boolean isKakaoLinked(String studentId) {
        if (studentId == null || studentId.isBlank()) return false;
        try {
            return kakaoTalkService.isLinked(studentId.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /* =========================
       Public APIs
       ========================= */

    @Transactional(readOnly = true)
    public List<LockerDto> getLockerGrid() {
        List<LockerDto> out = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Locker l = lockerRepo.findById(i).orElseThrow();
            out.add(new LockerDto(i, l.getState().name(), l.getReservedStudentId()));
        }
        return out;
    }

    @Transactional
    public String applyWithTransferImage(
            String studentId, String name, String phone, int lockerNumber,
            byte[] bytes, String ct, String filename
    ) {
        // ✅ 학번 중복 신청 방지 (학생 신청)
        String sid = (studentId == null) ? "" : studentId.trim();
        if (sid.isBlank()) throw new IllegalStateException("학번이 올바르지 않습니다.");

        if (appRepo.existsByStudentIdAndStatusIn(
                sid, List.of(Application.Status.PENDING, Application.Status.APPROVED)
        )) {
            throw new IllegalStateException("이미 신청(또는 사용) 중인 사물함이 있습니다.");
        }

        if (!isKakaoLinked(sid)) {
            throw new IllegalStateException("카카오 연동이 필요합니다. 먼저 카카오 연동을 완료한 뒤 다시 신청해주세요.");
        }

        Locker locker = lockerRepo.findById(lockerNumber).orElseThrow();
        if (locker.getState() != Locker.State.AVAILABLE)
            throw new IllegalStateException("이미 사용중인 사물함입니다.");

        Application app = new Application(sid, name, phone, lockerNumber, Application.Status.PENDING);
        app.setTransferImage(bytes, ct, filename);
        appRepo.save(app);

        locker.setState(Locker.State.RESERVED);
        locker.setReservedStudentId(sid);
        lockerRepo.save(locker);

        try {
            kakaoTalkService.sendToStudent(sid,
                    "사물함 신청 접수",
                    lockerNumber + "번 사물함 신청이 접수되었습니다.\n관리자 승인 후 확인코드가 발급됩니다.");
        } catch (Exception ignore) {}

        return "";
    }

    /* =========================
       Admin
       ========================= */

    @Transactional
    public void approve(long id) {
        Application app = appRepo.findById(id).orElseThrow();

        app.setStatus(Application.Status.APPROVED);

        Instant now = Instant.now();
        app.setApprovedAt(now);
        app.setExpiresAt(resolveExpiresAtOrDefault(now));

        String codePlain = null;
        if (app.getLookupCodeHash() == null || app.getLookupCodeHash().isBlank()) {
            codePlain = String.valueOf((int)(Math.random()*900000)+100000);
            app.setLookupCodeHash(passwordEncoder.encode(codePlain));
        }

        app.clearTransferImage();
        appRepo.save(app);

        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElseThrow();
        locker.setState(Locker.State.APPROVED);
        lockerRepo.save(locker);

        if (codePlain != null) {
            StudentAccount acc = studentRepo.findById(app.getStudentId()).orElse(null);
            if (acc == null) {
                acc = new StudentAccount(app.getStudentId(), passwordEncoder.encode(codePlain));
            } else {
                acc.setPasswordHash(passwordEncoder.encode(codePlain));
            }
            studentRepo.save(acc);
        }

        try {
            if (codePlain != null) {
                kakaoTalkService.sendToStudent(app.getStudentId(),
                        "사물함 승인 완료",
                        app.getLockerNumber() + "번 사물함 사용이 승인되었습니다.\n"
                                + "확인코드: " + codePlain + "\n"
                                + "※ 이 확인코드는 '나의 사물함 조회' 및 비밀번호 재설정에 사용됩니다.");
            } else {
                kakaoTalkService.sendToStudent(app.getStudentId(),
                        "사물함 승인 완료",
                        app.getLockerNumber() + "번 사물함 사용이 승인되었습니다.\n"
                                + "※ 확인코드는 이전에 발급된 코드를 사용하세요.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void reject(long id) {
        Application app = appRepo.findById(id).orElseThrow();

        try {
            kakaoTalkService.sendToStudent(app.getStudentId(),
                    "사물함 신청 거절",
                    app.getLockerNumber() + "번 신청이 거절되었습니다.");
        } catch (Exception ignore) {}

        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElseThrow();
        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);

        appRepo.delete(app);
    }

    /* =========================
       Student Account APIs
       ========================= */

    public StudentLoginDto studentLogin(String studentId, String password) {
        auth(studentId, password);

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElse(null);
        if (app == null)
            return new StudentLoginDto(studentId, "NONE", null, "신청 내역 없음");

        return new StudentLoginDto(
                studentId,
                app.getStatus().name(),
                app.getLockerNumber(),
                "조회 성공"
        );
    }

    public void changeStudentPassword(String studentId, String current, String next) {
        if (studentId == null || studentId.isBlank()) throw new IllegalArgumentException("studentId required");
        if (current == null) current = "";
        if (next == null || next.isBlank()) throw new IllegalArgumentException("새 비밀번호를 입력하세요.");

        String sid = studentId.trim();

        StudentAccount acc = studentRepo.findById(sid).orElse(null);
        if (acc != null && passwordEncoder.matches(current, acc.getPasswordHash())) {
            acc.setPasswordHash(passwordEncoder.encode(next));
            studentRepo.save(acc);
            return;
        }

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(sid).orElse(null);
        if (app == null || app.getLookupCodeHash() == null || app.getLookupCodeHash().isBlank()) {
            throw new IllegalStateException("비밀번호가 올바르지 않습니다.");
        }

        if (!passwordEncoder.matches(current, app.getLookupCodeHash())) {
            throw new IllegalStateException("비밀번호가 올바르지 않습니다.");
        }

        if (acc == null) {
            acc = new StudentAccount(sid, passwordEncoder.encode(next));
        } else {
            acc.setPasswordHash(passwordEncoder.encode(next));
        }
        studentRepo.save(acc);
    }

    public MyLockerDto getMyLockerByAccount(String studentId, String password) {
        auth(studentId, password);

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElse(null);
        if (app == null)
            return new MyLockerDto("NONE","신청 내역 없음",studentId,null,null,null,null, null);

        return new MyLockerDto(
                app.getStatus().name(),
                "조회 성공",
                app.getStudentId(),
                app.getName(),
                app.getPhone(),
                app.getLockerNumber(),
                app.getMemo(),
                app.getExpiresAt()
        );
    }

    public MyLockerDto getMyLocker(String studentId, String code) {
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElseThrow();
        if (!passwordEncoder.matches(code, app.getLookupCodeHash()))
            throw new IllegalStateException("확인코드 불일치");

        return new MyLockerDto(
                app.getStatus().name(),
                "조회 성공",
                app.getStudentId(),
                app.getName(),
                app.getPhone(),
                app.getLockerNumber(),
                app.getMemo(),
                app.getExpiresAt()
        );
    }

    public void saveMyMemo(String studentId, String code, String memo) {
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElseThrow();
        if (!passwordEncoder.matches(code, app.getLookupCodeHash()))
            throw new IllegalStateException("확인코드 불일치");
        app.setMemo(memo);
        appRepo.save(app);
    }

    public void saveMyMemoByAccount(String studentId, String password, String memo) {
        auth(studentId,password);
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElseThrow();
        app.setMemo(memo);
        appRepo.save(app);
    }

    public void emptyMyLocker(String studentId, String code) {
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElseThrow();
        if (!passwordEncoder.matches(code, app.getLookupCodeHash()))
            throw new IllegalStateException("확인코드 불일치");

        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElseThrow();
        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);

        appRepo.delete(app);
        studentRepo.deleteById(studentId);
    }

    public void emptyMyLockerByAccount(String studentId, String password) {
        auth(studentId,password);
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId).orElseThrow();

        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElseThrow();
        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);

        appRepo.delete(app);
        studentRepo.deleteById(studentId);
    }

    /* =========================
       Extra Admin
       ========================= */

    @Transactional
    public void clearApprovedLocker(int lockerNumber) {
        Locker locker = lockerRepo.findById(lockerNumber).orElseThrow();
        String sid = locker.getReservedStudentId();

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);

        appRepo.deleteByLockerNumber(lockerNumber);
        if (sid != null) studentRepo.deleteById(sid);
    }

    @Transactional
    public void resetAll() {
        appRepo.deleteAll();
        studentRepo.deleteAll();
        lockerRepo.findAll().forEach(l -> {
            l.setState(Locker.State.AVAILABLE);
            l.setReservedStudentId(null);
            lockerRepo.save(l);
        });
    }

    public List<PendingDto> getPendingList() {
        List<Application> list = appRepo.findByStatus(Application.Status.PENDING);
        List<PendingDto> out = new ArrayList<>();
        for (Application a : list) {
            out.add(new PendingDto(a.getId(), a.getStudentId(), a.getName(), a.getPhone(), a.getLockerNumber()));
        }
        return out;
    }

    public TransferImageDto getTransferImage(long id) {
        Application a = appRepo.findById(id).orElseThrow();
        return new TransferImageDto(
                a.getTransferImage(),
                a.getTransferImageContentType(),
                a.getTransferImageFilename()
        );
    }

    public String adminAssignApproved(String studentId, String name, String phone, int lockerNumber) {
        String sid = (studentId == null) ? "" : studentId.trim();
        if (sid.isBlank()) throw new IllegalStateException("학번이 올바르지 않습니다.");

        // ✅ [추가] 관리자 배정에서도 학번 중복 방지
        // 이미 PENDING/APPROVED가 있으면, 어떤 사물함도 배정 불가
        if (appRepo.existsByStudentIdAndStatusIn(
                sid, List.of(Application.Status.PENDING, Application.Status.APPROVED)
        )) {
            throw new IllegalStateException("이미 다른 사물함을 신청/사용 중인 학번입니다.");
        }

        Locker locker = lockerRepo.findById(lockerNumber).orElseThrow();
        if (locker.getState()!=Locker.State.AVAILABLE)
            throw new IllegalStateException("이미 사용중");

        Application app = new Application(sid,name,phone,lockerNumber, Application.Status.APPROVED);

        Instant now = Instant.now();
        app.setApprovedAt(now);
        app.setExpiresAt(resolveExpiresAtOrDefault(now));

        String code = String.valueOf((int)(Math.random()*900000)+100000);
        app.setLookupCodeHash(passwordEncoder.encode(code));
        appRepo.save(app);

        locker.setState(Locker.State.APPROVED);
        locker.setReservedStudentId(sid);
        lockerRepo.save(locker);

        StudentAccount acc = studentRepo.findById(sid).orElse(null);
        if (acc == null) acc = new StudentAccount(sid, passwordEncoder.encode(code));
        else acc.setPasswordHash(passwordEncoder.encode(code));
        studentRepo.save(acc);

        try {
            kakaoTalkService.sendToStudent(sid,
                    "사물함 승인 완료",
                    lockerNumber + "번 사물함 사용이 승인되었습니다.\n확인코드: " + code);
        } catch (Exception ignore) {}

        return code;
    }

    @Transactional(readOnly = true)
    public MyStatusDto getMyStatus(String studentId, String code) {
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId)
                .orElse(null);

        if (app == null) {
            return new MyStatusDto(studentId, "NONE", null, "신청 내역이 없습니다.");
        }

        if (app.getLookupCodeHash() == null || app.getLookupCodeHash().isBlank()
                || !passwordEncoder.matches(code, app.getLookupCodeHash())) {
            return new MyStatusDto(studentId, "ERROR", null, "확인코드가 올바르지 않습니다.");
        }

        String st = app.getStatus().name();
        String msg;

        if ("PENDING".equals(st)) {
            msg = "신청이 접수되었습니다. 관리자 승인 대기 중입니다.";
        } else if ("APPROVED".equals(st)) {
            msg = "사물함 사용이 승인되었습니다.";
        } else if ("EXPIRED".equals(st)) {
            msg = "사용기간이 만료되었습니다.";
        } else {
            msg = "상태: " + st;
        }

        return new MyStatusDto(
                studentId,
                st,
                app.getLockerNumber(),
                msg
        );
    }
}
