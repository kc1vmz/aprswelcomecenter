ALTER TABLE communication_policies ADD COLUMN content_source VARCHAR(16) DEFAULT 'TEXT' NOT NULL;
ALTER TABLE communication_policies ADD COLUMN topic_id VARCHAR(40);
CREATE TABLE policy_topic_parameters (
    policy_id UUID NOT NULL REFERENCES communication_policies(id) ON DELETE CASCADE,
    parameter_name VARCHAR(255) NOT NULL,
    parameter_value VARCHAR(4000) NOT NULL,
    PRIMARY KEY(policy_id, parameter_name)
);
CREATE TABLE policy_content_results (
    policy_id UUID NOT NULL REFERENCES communication_policies(id) ON DELETE CASCADE,
    policy_version BIGINT NOT NULL,
    occurrence_key VARCHAR(180) NOT NULL,
    topic_id VARCHAR(40) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content_status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    error_message VARCHAR(4000),
    resolved_text VARCHAR(4000) NOT NULL,
    PRIMARY KEY(policy_id, policy_version, occurrence_key)
);
