package com.cse.locker.repo;

import com.cse.locker.domain.Application;
import com.cse.locker.domain.Application.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 특정 상태(PENDING/APPROVED)의 신청 목록 조회
    List<Application> findByStatus(Status status);

    // 사물함 번호 기준으로 신청 삭제
    void deleteByLockerNumber(int lockerNumber);

    // 학번 기준 최신 신청 1건 조회(id 내림차순)
    Optional<Application> findTopByStudentIdOrderByIdDesc(String studentId);

    // 학번 + 상태 기준 최신 신청 1건 조회(id 내림차순)
    Optional<Application> findTopByStudentIdAndStatusOrderByIdDesc(String studentId, Status status);

    // 학번 기준으로 '활성' 신청(예: PENDING/APPROVED)이 존재하는지 확인
    boolean existsByStudentIdAndStatusIn(String studentId, Collection<Status> statuses);
}
