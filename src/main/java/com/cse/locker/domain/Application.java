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
    private Instant createdAt; // 최초 신청 시각

    @Column(columnDefinition = "text")
    private String memo; // 사물함 메모(물품 기록)

    @Column(name = "lookup_code_hash")
    private String lookupCodeHash; // 조회용 코드 해시

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

    protected Application() {
        // JPA 기본 생성자(필수)
    }

    /**
     * ✅ (A 방식) 기존 LockerService 코드 호환용 생성자
     * - status를 외부에서 넣을 수 있어야 함
     */
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

    // -----------------------------
    // getters
    // -----------------------------
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public int getLockerNumber() { return lockerNumber; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMemo() { return memo; }
    public String getLookupCodeHash() { return lookupCodeHash; }

    public byte[] getTransferImage() { return transferImage; }
    public String getTransferImageContentType() { return transferImageContentType; }
    public String getTransferImageFilename() { return transferImageFilename; }
    public Instant getTransferImageUploadedAt() { return transferImageUploadedAt; }

    // -----------------------------
    // setters
    // -----------------------------
    public void setStatus(Status status) {
        this.status = status;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public void setLookupCodeHash(String lookupCodeHash) {
        this.lookupCodeHash = lookupCodeHash;
    }

    // -----------------------------
    // transfer image helpers
    // -----------------------------
    public void setTransferImage(byte[] bytes, String contentType, String filename) {
        this.transferImage = bytes;
        this.transferImageContentType = contentType;
        this.transferImageFilename = filename;
        this.transferImageUploadedAt = Instant.now();
    }

    /**
     * ✅ 승인 후에는 이미지 저장하지 않기: 즉시 폐기
     * - LockerService.approve()에서 호출
     */
    public void clearTransferImage() {
        this.transferImage = null;
        this.transferImageContentType = null;
        this.transferImageFilename = null;
        this.transferImageUploadedAt = null;
    }
}
