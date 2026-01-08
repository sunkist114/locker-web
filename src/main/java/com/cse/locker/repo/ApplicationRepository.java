package com.cse.locker.repo;

import com.cse.locker.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByStatus(Application.Status status);

    void deleteByLockerNumber(int lockerNumber);

    // ✅ 학생 “내 신청 조회”용
    Optional<Application> findTopByStudentIdOrderByIdDesc(String studentId);
}
