package com.cse.locker.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "lockers")
public class Locker {

    public enum State { AVAILABLE, RESERVED, APPROVED }

    @Id
    private Integer lockerNumber; // 1..50 (사물함 번호가 PK)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column
    private String reservedStudentId; // 예약한 학생 학번(없으면 null)

    protected Locker() {
        // JPA 기본 생성자(필수)
    }

    public Locker(Integer lockerNumber) {
        // 새 사물함 엔티티 생성 시 기본값 세팅
        this.lockerNumber = lockerNumber;
        this.state = State.AVAILABLE;
        this.reservedStudentId = null;
    }

    public Integer getLockerNumber() { return lockerNumber; }
    public State getState() { return state; }
    public String getReservedStudentId() { return reservedStudentId; }

    public void setState(State state) { this.state = state; }
    public void setReservedStudentId(String reservedStudentId) { this.reservedStudentId = reservedStudentId; }
}
