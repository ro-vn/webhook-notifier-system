-- Webhook Notifier System — Database Initialization
-- Creates schema and seeds demo data for local development

CREATE TABLE webhook_configs (
    id BIGINT PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    target_url VARCHAR(1024) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    UNIQUE(account_id, event_type)
);

CREATE TABLE dead_letter_queue (
    id BIGINT PRIMARY KEY,
    account_id VARCHAR(255),
    event_type VARCHAR(255),
    payload JSONB NOT NULL,
    failed_reason TEXT,
    failed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Data: Route all events to local mock-receiver
INSERT INTO webhook_configs (account_id, event_type, target_url)
VALUES ('whale_account_1', 'subscriber.created', 'http://mock-receiver:8081/api/webhook');

INSERT INTO webhook_configs (account_id, event_type, target_url)
VALUES ('small_account_1', 'subscriber.created', 'http://mock-receiver:8081/api/webhook');
