package com.cse.locker.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications")
public class Application {

    public enum Status {
        PENDING, APPROVED, EXPIRED
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

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "lookup_code_hash")
    private String lookupCodeHash;

    // ✅ 승인/만료 알림용 필드
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "notified_d7")
    private boolean notifiedD7;

    @Column(name = "notified_d1")
    private boolean notifiedD1;

    // -----------------------------
    // Deposit transfer image (store only while PENDING)
    // -----------------------------
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "transfer_image", columnDefinition = "bytea")
    private byte[] transferImage;

    @Column(name = "transfer_image_content_type")
    private String transferImageContentType;

    @Column(name = "transfer_image_filename")
    private String transferImageFilename;

    @Column(name = "transfer_image_uploaded_at")
    private Instant transferImageUploadedAt;

    protected Application() {}

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

    public Instant getApprovedAt() { return approvedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public boolean isNotifiedD7() { return notifiedD7; }
    public boolean isNotifiedD1() { return notifiedD1; }

    public byte[] getTransferImage() { return transferImage; }
    public String getTransferImageContentType() { return transferImageContentType; }
    public String getTransferImageFilename() { return transferImageFilename; }
    public Instant getTransferImageUploadedAt() { return transferImageUploadedAt; }

    // setters
    public void setStatus(Status status) { this.status = status; }
    public void setMemo(String memo) { this.memo = memo; }
    public void setLookupCodeHash(String lookupCodeHash) { this.lookupCodeHash = lookupCodeHash; }

    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setExpiredAt(Instant expiredAt) { this.expiredAt = expiredAt; }
    public void setNotifiedD7(boolean notifiedD7) { this.notifiedD7 = notifiedD7; }
    public void setNotifiedD1(boolean notifiedD1) { this.notifiedD1 = notifiedD1; }

    // transfer image helpers
    public void setTransferImage(byte[] bytes, String contentType, String filename) {
        this.transferImage = bytes;
        this.transferImageContentType = contentType;
        this.transferImageFilename = filename;
        this.transferImageUploadedAt = Instant.now();
    }

    public void clearTransferImage() {
        this.transferImage = null;
        this.transferImageContentType = null;
        this.transferImageFilename = null;
        this.transferImageUploadedAt = null;
    }
}
