package com.rnvo.notifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rnvo.notifier.model.DlqRecord;
import com.rnvo.notifier.model.EventPayload;
import com.rnvo.notifier.repository.DlqRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock private DlqRecordRepository repository;

    private DlqService dlqService;

    @BeforeEach
    void setUp() {
        dlqService = new DlqService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("Saves event to DLQ with correct fields")
    void shouldSaveToDlq() {
        EventPayload event = new EventPayload("account_1", "subscriber.created",
                Map.of("email", "test@example.com"));

        dlqService.save(event, "All retries exhausted");

        ArgumentCaptor<DlqRecord> captor = ArgumentCaptor.forClass(DlqRecord.class);
        verify(repository).save(captor.capture());

        DlqRecord saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo("account_1");
        assertThat(saved.getEventType()).isEqualTo("subscriber.created");
        assertThat(saved.getFailedReason()).isEqualTo("All retries exhausted");
        assertThat(saved.getPayload()).contains("account_1");
        assertThat(saved.getFailedAt()).isNotNull();
    }
}
