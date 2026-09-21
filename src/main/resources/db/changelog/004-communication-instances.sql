CREATE TABLE IF NOT EXISTS communication_instances (
 id UUID PRIMARY KEY, version BIGINT DEFAULT 0 NOT NULL,
 type VARCHAR(255) NOT NULL CHECK(type IN ('APRS_IS','KISS_TCP','KISS_SERIAL')),
 state VARCHAR(255) DEFAULT 'ACTIVE' NOT NULL CHECK(state IN ('ACTIVE','PAUSED')),
 host VARCHAR(255), port INTEGER, username VARCHAR(255), passcode VARCHAR(255), aprs_filter VARCHAR(255),
 serial_device VARCHAR(255), baud_rate INTEGER, init_command1 VARCHAR(255), init_command2 VARCHAR(255), digi_path VARCHAR(255),
 legacy_settings_id UUID, legacy_source VARCHAR(255), UNIQUE(legacy_settings_id,legacy_source)
);
INSERT INTO communication_instances(id,type,state,host,port,username,passcode,aprs_filter,legacy_settings_id,legacy_source)
 SELECT RANDOM_UUID(),'APRS_IS',CASE WHEN using_internet_server THEN 'ACTIVE' ELSE 'PAUSED' END,
 internet_server_address,CASE WHEN REGEXP_LIKE(internet_server_port,'^[0-9]{1,5}$') THEN CAST(internet_server_port AS INTEGER) ELSE NULL END,
 internet_server_username,internet_server_passcode,aprs_filter,a.id,'APRS_IS'
 FROM application_settings a WHERE (using_internet_server OR NULLIF(TRIM(internet_server_address),'') IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM communication_instances c WHERE c.legacy_settings_id=a.id AND c.legacy_source='APRS_IS');
INSERT INTO communication_instances(id,type,state,host,port,serial_device,baud_rate,init_command1,init_command2,digi_path,legacy_settings_id,legacy_source)
 SELECT RANDOM_UUID(),CASE WHEN NULLIF(TRIM(kiss_host),'') IS NULL THEN 'KISS_SERIAL' ELSE 'KISS_TCP' END,
 CASE WHEN usingkiss THEN 'ACTIVE' ELSE 'PAUSED' END,kiss_host,
 CASE WHEN NULLIF(TRIM(kiss_host),'') IS NOT NULL AND REGEXP_LIKE(kiss_port,'^[0-9]{1,5}$') THEN CAST(kiss_port AS INTEGER) ELSE NULL END,
 CASE WHEN NULLIF(TRIM(kiss_host),'') IS NULL THEN kiss_port ELSE NULL END,
 CASE WHEN REGEXP_LIKE(kiss_baud_rate,'^[0-9]{1,7}$') THEN CAST(kiss_baud_rate AS INTEGER) ELSE NULL END,
 kiss_init_command1,kiss_init_command2,digi_path,a.id,
 CASE WHEN NULLIF(TRIM(kiss_host),'') IS NULL THEN 'APRS_KISS_SERIAL' ELSE 'APRS_KISS_TCPIP' END
 FROM application_settings a WHERE (usingkiss OR NULLIF(TRIM(kiss_host),'') IS NOT NULL OR NULLIF(TRIM(kiss_port),'') IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM communication_instances c WHERE c.legacy_settings_id=a.id AND c.legacy_source IN ('APRS_KISS_SERIAL','APRS_KISS_TCPIP'));
UPDATE station_packets h SET packet_processor_id=(SELECT CAST(c.id AS VARCHAR) FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id ORDER BY c.legacy_settings_id LIMIT 1)
 WHERE EXISTS(SELECT 1 FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id);
UPDATE station_messages h SET packet_processor_id=(SELECT CAST(c.id AS VARCHAR) FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id ORDER BY c.legacy_settings_id LIMIT 1)
 WHERE EXISTS(SELECT 1 FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id);
UPDATE communication_events h SET packet_processor_id=(SELECT CAST(c.id AS VARCHAR) FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id ORDER BY c.legacy_settings_id LIMIT 1)
 WHERE EXISTS(SELECT 1 FROM communication_instances c WHERE c.legacy_source=h.packet_processor_id);
