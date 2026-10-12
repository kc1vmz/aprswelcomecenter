ALTER TABLE application_settings ADD COLUMN message_max_retries INTEGER DEFAULT 3 NOT NULL CHECK (message_max_retries >= 0);
ALTER TABLE application_settings ADD COLUMN message_retry_interval_seconds INTEGER DEFAULT 60 NOT NULL CHECK (message_retry_interval_seconds > 0);
CREATE TABLE message_deliveries (
 id UUID PRIMARY KEY, identifier VARCHAR(5) NOT NULL, sender VARCHAR(10) NOT NULL, recipient VARCHAR(10) NOT NULL,
 route VARCHAR(36), center_id UUID, policy_id UUID, policy_version BIGINT, occurrence_key VARCHAR(180), claim_token UUID,
 content VARCHAR(4000), created_at TIMESTAMP WITH TIME ZONE NOT NULL, first_sent TIMESTAMP WITH TIME ZONE,
 last_sent TIMESTAMP WITH TIME ZONE, next_attempt TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 armed BOOLEAN DEFAULT FALSE NOT NULL, state VARCHAR(20) NOT NULL, attempts INTEGER DEFAULT 0 NOT NULL, lease UUID, lease_until TIMESTAMP WITH TIME ZONE,
 completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_delivery_match ON message_deliveries(sender,recipient,identifier);
CREATE INDEX idx_delivery_pending ON message_deliveries(state,next_attempt);
