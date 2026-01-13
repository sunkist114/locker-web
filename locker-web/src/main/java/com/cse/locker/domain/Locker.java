package com.cse.locker.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "lockers")
public class Locker {

    @Id
    private Integer lockerNumber; // 1..50

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column
    private String reservedStudentId;

    public enum State { AVAILABLE, RESERVED, APPROVED }

    // JPA 기본 생성자(필수)
    protected Locker() {}

    // ✅ 이 생성자가 있어야 new Locker(i)가 됨
    public Locker(Integer lockerNumber) {
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
