/*
 *
 * APRSWelcomeCenter
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
package com.kc1vmz.aprswc.database;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class LiquibaseMigrationTest {
    private DriverManagerDataSource database() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void migrate(DriverManagerDataSource dataSource) throws Exception {
        SpringLiquibase migration = new SpringLiquibase();
        migration.setDataSource(dataSource);
        migration.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        migration.afterPropertiesSet();
    }

    private void legacy(Connection c) throws Exception {
        ScriptUtils.executeSqlScript(c, new ClassPathResource("db/changelog/001-legacy-schema.sql"));
    }

    private String scalar(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement();
                ResultSet rows = s.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    @Test
    void freshInstallDefaultsToOpenAndEnforcesStatus() throws Exception {
        var ds = database();
        migrate(ds);
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            s.execute("insert into welcome_centers(id, callsign) values(random_uuid(), 'N1FRESH')");
            assertThat(scalar(c, "select status from welcome_centers")).isEqualTo("OPEN");
            assertThatThrownBy(() -> s.execute("update welcome_centers set status=null"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("update welcome_centers set status='BAD'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void adoptsLegacyDatabasePreservesDataAndNeverReopensClosedCenters() throws Exception {
        var ds = database();
        UUID center = UUID.randomUUID();
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            legacy(c);
            s.execute("insert into welcome_centers(id,callsign,name) values('" + center + "','N1OLD','Existing name')");
            s.execute("insert into welcome_regions(id,welcome_center_id,name) values(random_uuid(),'" + center
                    + "','Existing region')");
        }
        migrate(ds);
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            assertThat(scalar(c, "select status from welcome_centers")).isEqualTo("OPEN");
            assertThat(scalar(c, "select name from welcome_centers")).isEqualTo("Existing name");
            assertThat(scalar(c, "select name from welcome_regions")).isEqualTo("Existing region");
            s.execute("update welcome_centers set status='CLOSED'");
        }
        migrate(ds);
        try (Connection c = ds.getConnection()) {
            assertThat(scalar(c, "select status from welcome_centers")).isEqualTo("CLOSED");
            assertThat(scalar(c, "select count(*) from databasechangelog")).isEqualTo("8");
        }
    }

    @Test
    void migratesLegacyConnectionsAndHistoryOnlyOnce() throws Exception {
        for (boolean serial : new boolean[] {true, false}) {
            var ds = database();
            String oldKiss = serial ? "APRS_KISS_SERIAL" : "APRS_KISS_TCPIP";
            try (var c = ds.getConnection();
                    var statement = c.createStatement()) {
                legacy(c);
                statement.execute(
                        "insert into application_settings(id,using_internet_server,usingkiss,internet_server_address,internet_server_port,internet_server_username,internet_server_passcode,kiss_host,kiss_port,kiss_baud_rate,digi_path,map_tile_url) values(random_uuid(),true,false,'localhost','14580','N1TEST','123',"
                                + (serial ? "null,'COM3'" : "'localhost','8001'") + ",'9600','WIDE1-1','tiles')");
                statement.execute(
                        "insert into station_packets(id,packet_processor_id) values(random_uuid(),'APRS_IS'),(random_uuid(),'"
                                + oldKiss + "'),(random_uuid(),'unmapped')");
                statement.execute(
                        "insert into station_messages(id,packet_processor_id) values(random_uuid(),'" + oldKiss + "')");
                statement.execute("insert into welcome_centers(id,callsign) values(random_uuid(),'N1TEST')");
                statement.execute(
                        "insert into communication_events(id,welcome_center_id,packet_processor_id) select random_uuid(),id,'APRS_IS' from welcome_centers");
            }
            migrate(ds);
            String internet;
            try (var c = ds.getConnection()) {
                internet = scalar(c, "select cast(id as varchar) from communication_instances where type='APRS_IS'");
                UUID.fromString(internet);
                assertThat(scalar(c, "select state from communication_instances where type='APRS_IS'"))
                        .isEqualTo("ACTIVE");
                assertThat(scalar(c, "select state from communication_instances where type<>'APRS_IS'"))
                        .isEqualTo("PAUSED");
                assertThat(scalar(c, "select passcode from communication_instances where type='APRS_IS'"))
                        .isEqualTo("123");
                assertThat(scalar(c, "select map_tile_url from application_settings"))
                        .isEqualTo("tiles");
                assertThat(
                                scalar(
                                        c,
                                        "select count(*) from station_packets where packet_processor_id in (select cast(id as varchar) from communication_instances)"))
                        .isEqualTo("2");
                assertThat(scalar(c, "select packet_processor_id from communication_events"))
                        .isEqualTo(internet);
                assertThat(
                                scalar(
                                        c,
                                        "select count(*) from station_messages where packet_processor_id in (select cast(id as varchar) from communication_instances)"))
                        .isEqualTo("1");
            }
            migrate(ds);
            try (var c = ds.getConnection()) {
                assertThat(scalar(c, "select count(*) from communication_instances"))
                        .isEqualTo("2");
                assertThat(scalar(c, "select cast(id as varchar) from communication_instances where type='APRS_IS'"))
                        .isEqualTo(internet);
                assertThat(scalar(c, "select count(*) from station_packets where packet_processor_id='unmapped'"))
                        .isEqualTo("1");
            }
        }
    }

    @Test
    void stationAndMessageRetentionPreserveActivityAndDefaultsAcrossMigration() throws Exception {
        var ds = database();
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            legacy(c);
            statement.execute(
                    "insert into application_settings(id,using_internet_server,usingkiss) values(random_uuid(),false,false)");
            statement.execute("insert into stations(id,callsign) values(random_uuid(),'N1OLD')");
            statement.execute(
                    "insert into station_packets(id,callsign,received_time) values(random_uuid(),'N1OLD',timestamp '2026-09-01 12:34:56')");
            statement.execute(
                    "insert into station_messages(id,sent_time) values(random_uuid(),timestamp '2026-09-02 12:34:56'),(random_uuid(),null)");
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            assertThat(scalar(c, "select station_retention_days from application_settings"))
                    .isEqualTo("10");
            assertThat(scalar(c, "select message_retention_days from application_settings"))
                    .isEqualTo("10");
            assertThat(scalar(c, "select last_activity_time from stations")).startsWith("2026-09-01 12:34:56");
            assertThat(scalar(c, "select count(*) from station_messages where created_time is not null"))
                    .isEqualTo("2");
            statement.execute("delete from station_packets");
            statement.execute("update application_settings set station_retention_days=15,message_retention_days=20");
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select last_activity_time from stations")).startsWith("2026-09-01 12:34:56");
            assertThat(scalar(c, "select station_retention_days from application_settings"))
                    .isEqualTo("15");
            assertThat(scalar(c, "select message_retention_days from application_settings"))
                    .isEqualTo("20");
        }
    }

    @Test
    void rejectsPartialLegacySchemaBeforeBaselining() throws Exception {
        var ds = database();
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            s.execute("create table welcome_centers(id uuid primary key, callsign varchar(10))");
        }
        assertThatThrownBy(() -> migrate(ds)).hasStackTraceContaining("Database was not baselined");
        try (Connection c = ds.getConnection()) {
            assertThat(scalar(c, "select count(*) from databasechangelog")).isEqualTo("0");
        }
    }

    @Test
    void retentionBackfillsLegacySettingsAndPreservesEditedValueOnRestart() throws Exception {
        var ds = database();
        try (var c = ds.getConnection();
                var s = c.createStatement()) {
            legacy(c);
            s.execute(
                    "insert into application_settings(id,using_internet_server,usingkiss) values(random_uuid(),false,false)");
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var s = c.createStatement()) {
            assertThat(scalar(c, "select packet_retention_days from application_settings"))
                    .isEqualTo("1");
            assertThatThrownBy(() -> s.execute("update application_settings set packet_retention_days=0"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("update application_settings set packet_retention_days=null"))
                    .isInstanceOf(SQLException.class);
            s.execute("update application_settings set packet_retention_days=14");
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select packet_retention_days from application_settings"))
                    .isEqualTo("14");
        }
    }

    @Test
    void rejectsIncompatibleLegacyColumn() throws Exception {
        var ds = database();
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            legacy(c);
            s.execute("alter table welcome_centers alter column description varchar(20)");
        }
        assertThatThrownBy(() -> migrate(ds))
                .hasStackTraceContaining("Incompatible column WELCOME_CENTERS.DESCRIPTION");
    }
}
