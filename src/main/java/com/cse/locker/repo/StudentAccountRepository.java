package com.cse.locker.repo;

import com.cse.locker.domain.StudentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, String> {
}
