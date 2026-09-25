ALTER TABLE welcome_centers ADD COLUMN communication_mode VARCHAR(8) DEFAULT 'ALL' NOT NULL;
ALTER TABLE welcome_centers ADD CONSTRAINT ck_center_communication_mode CHECK (communication_mode IN ('ALL','SELECTED'));
ALTER TABLE welcome_centers ADD COLUMN routing_version BIGINT DEFAULT 0 NOT NULL;
CREATE TABLE welcome_center_communications (
    welcome_center_id UUID NOT NULL REFERENCES welcome_centers(id) ON DELETE CASCADE,
    communication_instance_id UUID NOT NULL REFERENCES communication_instances(id) ON DELETE CASCADE,
    PRIMARY KEY (welcome_center_id, communication_instance_id)
);
CREATE INDEX idx_center_communications_instance ON welcome_center_communications(communication_instance_id);
