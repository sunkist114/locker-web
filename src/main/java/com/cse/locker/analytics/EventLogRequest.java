package com.cse.locker.analytics;

public record EventLogRequest(
        String eventName,
        String pageName,
        String studentId,
        String meta
) {}
