package com.rnvo.notifier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rnvo.notifier.model.DlqRecord;
import com.rnvo.notifier.model.EventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.rnvo.notifier.repository.DlqRecordRepository;

import java.time.LocalDateTime;

/**
 * Persists failed events to the Dead Letter Queue in PostgreSQL.
 */
@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private final DlqRecordRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DlqService(DlqRecordRepository dlqRepository, ObjectMapper objectMapper) {
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves a failed event payload to the dead_letter_queue table.
     */
    public void save(EventPayload event, String reason) {
        try {
            DlqRecord record = new DlqRecord();
            record.setAccountId(event.getAccountId());
            record.setEventType(event.getEventType());
            record.setPayload(objectMapper.writeValueAsString(event));
            record.setFailedReason(reason);
            record.setFailedAt(LocalDateTime.now());
            dlqRepository.save(record);
            log.info("Saved to DLQ: account={}, event={}, reason={}",
                    event.getAccountId(), event.getEventType(), reason);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for DLQ: {}", e.getMessage());
        }
    }
}
