package com.cse.locker.repo;

import com.cse.locker.domain.Locker;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LockerRepository extends JpaRepository<Locker, Integer> {

    /**
     * 비관적 락(SELECT ... FOR UPDATE)으로 사물함을 조회.
     * 동시 예약 Race Condition 방지용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Locker l WHERE l.lockerNumber = :num")
    Optional<Locker> findByIdForUpdate(@Param("num") int num);
}
