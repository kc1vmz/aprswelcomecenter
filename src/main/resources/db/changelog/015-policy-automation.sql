ALTER TABLE communication_policies ALTER COLUMN communication_event_type VARCHAR(32);
ALTER TABLE communication_policies ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE communication_policies ADD COLUMN time_zone VARCHAR(80);
ALTER TABLE communication_policies ADD COLUMN scheduled_at TIMESTAMP;
ALTER TABLE communication_policies ADD COLUMN recurrence VARCHAR(8);
ALTER TABLE communication_policies ADD COLUMN schedule_hour INTEGER;
ALTER TABLE communication_policies ADD COLUMN schedule_minute INTEGER;
ALTER TABLE communication_policies ADD COLUMN shriek_code VARCHAR(12);
ALTER TABLE communication_policies ADD COLUMN next_run_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_policy_due ON communication_policies(next_run_at);
CREATE TABLE policy_executions (
    policy_id UUID NOT NULL REFERENCES communication_policies(id) ON DELETE CASCADE,
    occurrence_key VARCHAR(180) NOT NULL,
    instance_id VARCHAR(36),
    station_callsign VARCHAR(10),
    policy_version BIGINT NOT NULL,
    claim_token UUID NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    transmitted_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(policy_id, occurrence_key)
);
