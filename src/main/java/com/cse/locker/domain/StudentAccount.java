package com.cse.locker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 학생 계정(자동 생성)
 * - 승인 시: 아이디=학번, 비밀번호=토큰(확인코드)로 초기화
 * - 비밀번호는 BCrypt 해시로 저장
 */
@Entity
@Table(name = "student_account")
@Getter
@Setter
@NoArgsConstructor
public class StudentAccount {

    @Id
    @Column(length = 32, nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String passwordHash;

    public StudentAccount(String studentId, String passwordHash) {
        this.studentId = studentId;
        this.passwordHash = passwordHash;
    }
}
