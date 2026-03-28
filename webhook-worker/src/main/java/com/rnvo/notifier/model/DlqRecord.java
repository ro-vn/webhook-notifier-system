package com.rnvo.notifier.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * JPA entity for the Dead Letter Queue.
 * Stores events that exhausted all retry attempts.
 */
@Setter
@Getter
@Entity
@Table(name = "dead_letter_queue")
public class DlqRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "event_type")
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "failed_reason")
    private String failedReason;

    @Column(name = "failed_at")
    private LocalDateTime failedAt = LocalDateTime.now();

    public DlqRecord() {
    }
}
