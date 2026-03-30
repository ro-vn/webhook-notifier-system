package com.rnvo.notifier.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * POJO representing a deserialized Kafka event payload.
 * This is the JSON structure published by the mock-publisher.
 */
@Getter
@Setter
public class EventPayload {

    private String eventId;
    private String accountId;
    private String eventType;
    private Map<String, Object> data;
    private long timestamp;
    private int retryCount;
    private long nextAttemptAt;

    public EventPayload() {
    }

    public EventPayload(String eventId, String accountId, String eventType, Map<String, Object> data) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

}
