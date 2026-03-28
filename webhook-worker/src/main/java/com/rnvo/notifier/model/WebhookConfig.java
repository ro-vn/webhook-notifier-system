package com.rnvo.notifier.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * JPA entity representing a webhook routing configuration.
 * Maps an (account_id, event_type) pair to a target URL.
 */
@Setter
@Getter
@Entity
@Table(name = "webhook_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "event_type"}))
public class WebhookConfig implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "target_url", nullable = false, length = 1024)
    private String targetUrl;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public WebhookConfig() {
    }

    public WebhookConfig(String accountId, String eventType, String targetUrl) {
        this.accountId = accountId;
        this.eventType = eventType;
        this.targetUrl = targetUrl;
    }

}
