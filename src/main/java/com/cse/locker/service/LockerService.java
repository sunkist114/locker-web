package com.cse.locker.service;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Locker;
import com.cse.locker.domain.StudentAccount;
import com.cse.locker.repo.ApplicationRepository;
import com.cse.locker.repo.LockerRepository;
import com.cse.locker.repo.StudentAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class LockerService {

    private static final int LOCKER_MIN = 1;
    private static final int LOCKER_MAX = 50;

    private final LockerRepository lockerRepo;
    private final ApplicationRepository appRepo;
    private final StudentAccountRepository studentAccountRepo;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom random = new SecureRandom();

    public LockerService(LockerRepository lockerRepo,
                         ApplicationRepository appRepo,
                         StudentAccountRepository studentAccountRepo,
                         PasswordEncoder passwordEncoder) {
        this.lockerRepo = lockerRepo;
        this.appRepo = appRepo;
        this.studentAccountRepo = studentAccountRepo;
        this.passwordEncoder = passwordEncoder;
    }

    private void ensureStudentAccount(String studentId, String passwordHash) {
        if (studentId == null || studentId.isBlank()) return;
        if (passwordHash == null || passwordHash.isBlank()) return;

        // 이미 있으면 그대로 두고, 없으면 생성
        studentAccountRepo.findById(studentId).orElseGet(() ->
                studentAccountRepo.save(new StudentAccount(studentId, passwordHash))
        );
    }

    private void deleteStudentAccount(String studentId) {
        if (studentId == null || studentId.isBlank()) return;
        studentAccountRepo.deleteById(studentId);
    }

    private void requireStudentAccountAuth(String studentId, String password) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("학번이 비었습니다.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }

        StudentAccount acc = studentAccountRepo.findById(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("학번 또는 비밀번호가 올바르지 않습니다."));

        boolean ok = passwordEncoder.matches(password.trim(), acc.getPasswordHash());
        if (!ok) {
            throw new IllegalStateException("학번 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    public record LockerDto(int lockerNumber, String state, String studentId) {}
    public record PendingDto(long id, String studentId, String name, String phone, int lockerNumber) {}

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

    @PostConstruct
    @Transactional
    public void initLockers() {
        // 서버 시작 시 1..50 사물함 row가 없으면 생성
        for (int i = LOCKER_MIN; i <= LOCKER_MAX; i++) {
            final int n = i;
            lockerRepo.findById(n).orElseGet(() -> lockerRepo.save(new Locker(n)));
        }
    }

    @Transactional(readOnly = true)
    public List<LockerDto> getLockerGrid() {
        // 1..50 전체를 조회해서 프론트에 보여줄 상태 그리드 생성
        List<LockerDto> out = new ArrayList<>();
        for (int i = LOCKER_MIN; i <= LOCKER_MAX; i++) {
            final int n = i;
            Locker l = lockerRepo.findById(n)
                    .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + n));

            String status = l.getState().name();
            if ("RESERVED".equals(status)) status = "PENDING"; // UI 표시용 변환

            out.add(new LockerDto(
                    l.getLockerNumber(),
                    status,
                    l.getReservedStudentId()
            ));
        }
        return out;
    }

    private String generateLookupCode() {
        // 6자리 확인코드(100000~999999)
        int v = random.nextInt(900000) + 100000;
        return String.valueOf(v);
    }

    private Application requireValidLookup(String studentId, String code) {
        // 최신 신청 1건 기준으로 학번+확인코드(해시) 검증
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("학번이 비었습니다.");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("확인코드를 입력해주세요.");
        }

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("학번 또는 확인코드가 올바르지 않습니다."));

        if (app.getLookupCodeHash() == null || app.getLookupCodeHash().isBlank()) {
            throw new IllegalStateException("확인코드가 설정되어 있지 않습니다. 다시 신청해주세요.");
        }

        boolean ok = passwordEncoder.matches(code.trim(), app.getLookupCodeHash());
        if (!ok) {
            throw new IllegalStateException("학번 또는 확인코드가 올바르지 않습니다.");
        }
        return app;
    }

    private void preventDuplicateApply(String studentId) {
        // 같은 학번이 이미 PENDING/APPROVED면 중복 신청 방지
        var opt = appRepo.findTopByStudentIdOrderByIdDesc(studentId);
        if (opt.isEmpty()) return;

        Application last = opt.get();

        // 사물함이 AVAILABLE이면 과거 기록으로 보고 허용(방어 로직)
        Locker locker = lockerRepo.findById(last.getLockerNumber()).orElse(null);
        boolean stillUsing = locker != null && locker.getState() != Locker.State.AVAILABLE;

        if (stillUsing && (last.getStatus() == Application.Status.PENDING || last.getStatus() == Application.Status.APPROVED)) {
            throw new IllegalStateException("이미 신청 또는 사용 중인 사물함이 있습니다. (중복 신청 불가)");
        }
    }

    @Transactional
    public String apply(String studentId, String name, String phone, int lockerNumber) {
        // 학생 신청: AVAILABLE 사물함을 RESERVED로 바꾸고, 확인코드를 1회 반환
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

        return code;
    }

    @Transactional
    public String applyWithTransferImage(
            String studentId,
            String name,
            String phone,
            int lockerNumber,
            byte[] transferImageBytes,
            String transferImageContentType,
            String transferImageFilename
    ) {
        // 학생 신청 + 송금 이미지 업로드
        String code = apply(studentId, name, phone, lockerNumber);

        // 방금 저장된 최신 신청에 이미지 붙이기
        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("신청 저장에 실패했습니다."));
        app.setTransferImage(transferImageBytes, transferImageContentType, transferImageFilename);
        appRepo.save(app);

        return code;
    }

    @Transactional
    public String adminAssignApproved(String studentId, String name, String phone, int lockerNumber) {
        // 관리자 직권 승인: AVAILABLE 사물함에 사용자를 지정하고 즉시 APPROVED 처리
        studentId = studentId.trim();
        name = name.trim();
        phone = phone.trim();

        preventDuplicateApply(studentId);

        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        if (locker.getState() != Locker.State.AVAILABLE) {
            throw new IllegalStateException("비어있는(AVAILABLE) 사물함만 지정할 수 있습니다.");
        }

        String code = generateLookupCode();
        String hash = passwordEncoder.encode(code);

        Application app = new Application(studentId, name, phone, lockerNumber, Application.Status.APPROVED);
        app.setLookupCodeHash(hash);
        appRepo.save(app);

        // ✅ 승인 시 학생 계정 자동 생성 (아이디=학번, 비밀번호=확인코드)
        ensureStudentAccount(studentId, hash);

        locker.setState(Locker.State.APPROVED);
        locker.setReservedStudentId(studentId);
        lockerRepo.save(locker);

        return code;
    }

    @Transactional(readOnly = true)
    public List<PendingDto> getPendingList() {
        // 관리자: 대기(PENDING) 신청 목록
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
        // 관리자: PENDING 신청을 APPROVED로 변경 + 사물함 상태 APPROVED로 변경
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
        // ✅ 승인 후 송금 이미지는 저장하지 않기: 즉시 폐기
        app.clearTransferImage();
        appRepo.save(app);

        // ✅ 승인 시 학생 계정 자동 생성 (아이디=학번, 비밀번호=확인코드)
        // lookupCodeHash는 이미 BCrypt 해시로 저장되어 있으므로 그대로 사용
        ensureStudentAccount(app.getStudentId(), app.getLookupCodeHash());

        locker.setState(Locker.State.APPROVED);
        lockerRepo.save(locker);
    }

    public record TransferImageDto(byte[] bytes, String contentType, String filename) {}

    @Transactional(readOnly = true)
    public TransferImageDto getTransferImage(long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("없는 신청: " + applicationId));

        if (app.getStatus() != Application.Status.PENDING) {
            throw new IllegalStateException("대기(PENDING) 신청의 이미지만 확인할 수 있습니다.");
        }

        byte[] bytes = app.getTransferImage();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("업로드된 송금 이미지가 없습니다.");
        }

        String ct = app.getTransferImageContentType();
        if (ct == null || ct.isBlank()) ct = "application/octet-stream";

        return new TransferImageDto(bytes, ct, app.getTransferImageFilename());
    }

    @Transactional
    public void reject(long applicationId) {
        // 관리자: 신청 삭제, RESERVED였다면 사물함을 AVAILABLE로 복구
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
        // 관리자: 승인된 사물함 비우기(신청 기록 삭제 + 사물함 AVAILABLE)
        Locker locker = lockerRepo.findById(lockerNumber)
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + lockerNumber));

        if (locker.getState() != Locker.State.APPROVED) {
            throw new IllegalStateException("승인된 사물함만 비울 수 있습니다.");
        }

        // ✅ 학생 계정 삭제
        deleteStudentAccount(locker.getReservedStudentId());

        // 계정도 같이 삭제 (반납/초기화 때 계정이 쌓이지 않도록)
        String sid = locker.getReservedStudentId();
        // ✅ 학생 계정도 함께 삭제
        deleteStudentAccount(locker.getReservedStudentId());

        appRepo.deleteByLockerNumber(lockerNumber);
        deleteStudentAccount(sid);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }

    @Transactional
    public void resetAll() {
        // 관리자: 전체 초기화(신청 전체 삭제 + 모든 사물함 AVAILABLE)
        appRepo.deleteAll();
        studentAccountRepo.deleteAll();

        // ✅ 학생 계정도 전체 삭제
        studentAccountRepo.deleteAll();

        // ✅ 학생 계정도 전체 삭제
        studentAccountRepo.deleteAll();
        studentAccountRepo.deleteAll();

        for (int i = LOCKER_MIN; i <= LOCKER_MAX; i++) {
            final int n = i;
            Locker locker = lockerRepo.findById(n).orElseGet(() -> new Locker(n));
            locker.setState(Locker.State.AVAILABLE);
            locker.setReservedStudentId(null);
            lockerRepo.save(locker);
        }
    }

    @Transactional(readOnly = true)
    public MyStatusDto getMyStatus(String studentId, String code) {
        // 학생: 학번+확인코드로 "내 상태" 조회
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
        // 학생: 학번+확인코드로 "내 사물함 정보" 조회
        Application app = requireValidLookup(studentId, code);

        if (app.getStatus() != Application.Status.APPROVED) {
            if (app.getStatus() == Application.Status.PENDING) {
                return new MyLockerDto(
                        "PENDING",
                        "신청이 접수되었습니다. 관리자 승인을 기다려주세요.",
                        studentId,
                        null,
                        null,
                        app.getLockerNumber(),
                        null
                );
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
        // 학생: 승인 상태 + 본인 사물함인지 확인 후 메모 저장
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
        // 학생: 승인 상태 + 본인 사물함인지 확인 후 반납 처리
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
        // ✅ 반납 시 학생 계정 삭제(계정이 쌓이지 않도록)
        deleteStudentAccount(studentId);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }

    // =====================================================
    // 학생 계정 기반 기능
    // =====================================================

    public record StudentLoginDto(String status, String message, Integer lockerNumber) {}

    @Transactional(readOnly = true)
    public StudentLoginDto studentLogin(String studentId, String password) {
        // 승인 시 자동 생성된 계정으로 로그인
        requireStudentAccountAuth(studentId, password);

        var opt = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim());
        if (opt.isEmpty()) {
            return new StudentLoginDto("NONE", "현재 사용 중인 사물함이 없습니다.", null);
        }

        Application app = opt.get();
        Locker locker = lockerRepo.findById(app.getLockerNumber()).orElse(null);
        if (locker == null || locker.getState() == Locker.State.AVAILABLE) {
            return new StudentLoginDto("NONE", "현재 사용 중인 사물함이 없습니다.", null);
        }

        if (app.getStatus() != Application.Status.APPROVED) {
            return new StudentLoginDto("PENDING", "신청이 접수되었습니다. 관리자 승인을 기다려주세요.", app.getLockerNumber());
        }

        return new StudentLoginDto("APPROVED", "승인되어 사용 중입니다.", app.getLockerNumber());
    }

    @Transactional(readOnly = true)
    public MyLockerDto getMyLockerByAccount(String studentId, String password) {
        requireStudentAccountAuth(studentId, password);

        var opt = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim());
        if (opt.isEmpty()) {
            return new MyLockerDto("NONE", "현재 사용 중인 사물함이 없습니다.", studentId, null, null, null, null);
        }
        Application app = opt.get();

        if (app.getStatus() != Application.Status.APPROVED) {
            if (app.getStatus() == Application.Status.PENDING) {
                return new MyLockerDto(
                        "PENDING",
                        "신청이 접수되었습니다. 관리자 승인을 기다려주세요.",
                        studentId,
                        null,
                        null,
                        app.getLockerNumber(),
                        null
                );
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
    public void saveMyMemoByAccount(String studentId, String password, String memo) {
        requireStudentAccountAuth(studentId, password);

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("현재 사용 중인 사물함이 없습니다."));

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
    public void emptyMyLockerByAccount(String studentId, String password) {
        requireStudentAccountAuth(studentId, password);

        Application app = appRepo.findTopByStudentIdOrderByIdDesc(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("승인된 사물함이 없습니다."));

        if (app.getStatus() != Application.Status.APPROVED) {
            throw new IllegalStateException("승인된 사물함이 없습니다.");
        }

        Locker locker = lockerRepo.findById(app.getLockerNumber())
                .orElseThrow(() -> new IllegalArgumentException("없는 사물함: " + app.getLockerNumber()));

        if (locker.getState() != Locker.State.APPROVED || !studentId.equals(locker.getReservedStudentId())) {
            throw new IllegalStateException("현재 사용 중인 사물함이 아닙니다.");
        }

        appRepo.delete(app);
        deleteStudentAccount(studentId);

        locker.setState(Locker.State.AVAILABLE);
        locker.setReservedStudentId(null);
        lockerRepo.save(locker);
    }

    @Transactional
    public void changeStudentPassword(String studentId, String currentPassword, String newPassword) {
        requireStudentAccountAuth(studentId, currentPassword);
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호를 입력해주세요.");
        }

        StudentAccount acc = studentAccountRepo.findById(studentId.trim())
                .orElseThrow(() -> new IllegalStateException("학번 또는 비밀번호가 올바르지 않습니다."));
        acc.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        studentAccountRepo.save(acc);
    }
}
