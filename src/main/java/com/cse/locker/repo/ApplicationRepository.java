package com.cse.locker.repo;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Application.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByStatus(Status status);

    void deleteByLockerNumber(int lockerNumber);

    Optional<Application> findTopByStudentIdOrderByIdDesc(String studentId);

    Optional<Application> findTopByStudentIdAndStatusOrderByIdDesc(String studentId, Status status);

    // ✅ 학번 기준으로 '활성' 신청(대기/승인)이 존재하는지 확인
    boolean existsByStudentIdAndStatusIn(String studentId, Collection<Status> statuses);
}
