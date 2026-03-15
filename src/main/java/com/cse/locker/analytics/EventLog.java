package com.cse.locker.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "event_logs",
        indexes = @Index(name = "idx_event_occurred_at", columnList = "occurred_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "page_name", length = 64)
    private String pageName;

    @Column(name = "student_id", length = 64)
    private String studentId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "text")
    private String meta;
}
