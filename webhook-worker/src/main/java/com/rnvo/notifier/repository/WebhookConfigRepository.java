package com.rnvo.notifier.repository;

import com.rnvo.notifier.model.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA com.rnvo.notifier.repository for webhook routing configurations.
 */
@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    Optional<WebhookConfig> findByAccountIdAndEventType(String accountId, String eventType);
}
