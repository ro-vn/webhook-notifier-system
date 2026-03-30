package com.rnvo.notifier.repository;

import com.rnvo.notifier.model.DlqRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA com.rnvo.notifier.repository for the Dead Letter Queue.
 */
@Repository
public interface DlqRecordRepository extends JpaRepository<DlqRecord, Long> {

    /**
     * Find the oldest DLQ record to compute DLQ age.
     */
    Optional<DlqRecord> findTopByOrderByFailedAtAsc();
}
