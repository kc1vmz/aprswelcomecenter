CREATE TABLE message_identifier_counter (id INTEGER PRIMARY KEY CHECK (id = 1), next_value INTEGER NOT NULL CHECK (next_value >= 0 AND next_value < 60466176));
INSERT INTO message_identifier_counter (id, next_value) VALUES (1, 1);
ALTER TABLE station_messages ADD COLUMN message_identifier VARCHAR(5);
