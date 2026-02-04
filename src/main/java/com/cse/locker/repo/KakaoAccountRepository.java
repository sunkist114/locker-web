package com.cse.locker.repo;

import com.cse.locker.domain.KakaoAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KakaoAccountRepository extends JpaRepository<KakaoAccount, Long> {

    /** studentId 로 카카오 계정(토큰) 조회 */
    Optional<KakaoAccount> findByStudentId(String studentId);

    /** studentId 로 카카오 계정(토큰) 삭제 */
    void deleteByStudentId(String studentId);
}
