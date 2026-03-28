package com.rnvo.notifier.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity for the Dead Letter Queue.
 * Stores events that exhausted all retry attempts.
 */
@Entity
@Table(name = "dead_letter_queue")
public class DlqRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @Column(name = "account_id")
    private String accountId;

    @Setter
    @Getter
    @Column(name = "event_type")
    private String eventType;

    @Setter
    @Getter
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Setter
    @Getter
    @Column(name = "failed_reason")
    private String failedReason;

    @Setter
    @Getter
    @Column(name = "failed_at")
    private LocalDateTime failedAt = LocalDateTime.now();

    public DlqRecord() {
    }
}
