package com.cse.locker.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findTop200ByOrderByOccurredAtDesc();
}
