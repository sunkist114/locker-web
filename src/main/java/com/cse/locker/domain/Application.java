package com.cse.locker.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications")
public class Application {

    public enum Status {
        PENDING, APPROVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private int lockerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt; // 생성 시각(최초 저장 시 자동 세팅)

    @Column(columnDefinition = "text")
    private String memo; // 사물함 메모(물건 기록)

    @Column(name = "lookup_code_hash")
    private String lookupCodeHash; // 조회용 코드 해시

    protected Application() {
        // JPA 기본 생성자(필수)
    }

    public Application(String studentId, String name, String phone, int lockerNumber, Status status) {
        // 서비스에서 새 신청 생성할 때 사용하는 생성자
        this.studentId = studentId;
        this.name = name;
        this.phone = phone;
        this.lockerNumber = lockerNumber;
        this.status = status;
        this.memo = "";
    }

    @PrePersist
    void prePersist() {
        // 엔티티가 DB에 처음 저장되기 직전에 createdAt 자동 세팅
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public int getLockerNumber() { return lockerNumber; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMemo() { return memo; }
    public String getLookupCodeHash() { return lookupCodeHash; }

    // setters
    public void setStatus(Status status) { this.status = status; }
    public void setMemo(String memo) { this.memo = memo; }
    public void setLookupCodeHash(String lookupCodeHash) { this.lookupCodeHash = lookupCodeHash; }
}
