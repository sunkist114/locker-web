package com.cse.locker.repo;

import com.cse.locker.domain.Locker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockerRepository extends JpaRepository<Locker, Integer> {}
