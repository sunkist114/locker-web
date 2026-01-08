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

    // ✅ DB에 created_at NOT NULL이 이미 있으므로 엔티티에도 반드시 맞춰야 함
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Application() {
        // JPA
    }

    public Application(String studentId, String name, String phone, int lockerNumber, Status status) {
        this.studentId = studentId;
        this.name = name;
        this.phone = phone;
        this.lockerNumber = lockerNumber;
        this.status = status;
    }

    // ✅ insert 전에 createdAt 자동 주입
    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    // getters
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public int getLockerNumber() { return lockerNumber; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    // setters
    public void setStatus(Status status) { this.status = status; }
}
