CREATE TABLE IF NOT EXISTS points_of_interest (
 id UUID PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, beacon_generation BIGINT NOT NULL DEFAULT 0,
 welcome_center_id UUID NOT NULL REFERENCES welcome_centers(id),
 name VARCHAR(9) NOT NULL UNIQUE CHECK(REGEXP_LIKE(name,'^[A-Z0-9]{1,9}$')),
 description VARCHAR(40), latitude VARCHAR(8) NOT NULL, longitude VARCHAR(9) NOT NULL,
 symbol_code VARCHAR(1) NOT NULL, symbol_table_id VARCHAR(1) NOT NULL,
 temporarily_unavailable BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_poi_center ON points_of_interest(welcome_center_id);
CREATE TABLE IF NOT EXISTS aprs_object_name_lock(id INTEGER PRIMARY KEY);
INSERT INTO aprs_object_name_lock(id) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM aprs_object_name_lock WHERE id=1);
