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
    private Instant createdAt;

    // ✅ 사물함 메모(물건 기록)
    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "lookup_code_hash")
    private String lookupCodeHash;

    // ✅ JPA용 기본 생성자 (그대로 둬야 함)
    protected Application() {}

    // ✅ 서비스에서 생성할 때 쓰는 생성자 (이게 핵심)
    public Application(String studentId, String name, String phone, int lockerNumber, Status status) {
        this.studentId = studentId;
        this.name = name;
        this.phone = phone;
        this.lockerNumber = lockerNumber;
        this.status = status;
        this.memo = "";
    }

    @PrePersist
    void prePersist() {
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

    // setters (필요하면)
    public void setStatus(Status status) { this.status = status; }
    public void setMemo(String memo) { this.memo = memo; }
    public void setLookupCodeHash(String lookupCodeHash) { this.lookupCodeHash = lookupCodeHash; }
}
