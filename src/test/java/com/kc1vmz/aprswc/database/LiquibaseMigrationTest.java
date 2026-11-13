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
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
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
    void loggingDefaultsOffOnUpgradeAndKeepsSavedChoice() throws Exception {
        var ds = database();
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            legacy(c);
            statement.execute(
                    "insert into application_settings(id,using_internet_server,usingkiss) values(random_uuid(),false,false)");
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            assertThat(scalar(c, "select packet_logging_enabled from application_settings"))
                    .isEqualTo("FALSE");
            statement.execute("update application_settings set packet_logging_enabled=true");
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select packet_logging_enabled from application_settings"))
                    .isEqualTo("TRUE");
        }
    }

    @Test
    void legacyPacketsBecomeReceivedAndDirectionSurvivesRepeatMigration() throws Exception {
        var ds = database();
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            legacy(c);
            statement.execute("insert into station_packets(id,callsign,command) values(random_uuid(),'N1TEST','old')");
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            assertThat(scalar(c, "select direction from station_packets")).isEqualTo("RX");
            statement.execute(
                    "insert into station_packets(id,callsign,command,direction) values(random_uuid(),'N2TEST',repeat('x',512),'TX')");
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select direction from station_packets where callsign='N2TEST'"))
                    .isEqualTo("TX");
        }
    }

    @Test
    void tinyTopicsDefaultIsPersistedAndEditsSurviveMigration() throws Exception {
        for (boolean existingSettings : new boolean[] {false, true}) {
            var ds = database();
            try (var c = ds.getConnection();
                    var statement = c.createStatement()) {
                legacy(c);
                if (existingSettings)
                    statement.execute(
                            "insert into application_settings(id,using_internet_server,usingkiss) values(random_uuid(),false,false)");
            }
            migrate(ds);
            try (var c = ds.getConnection();
                    var statement = c.createStatement()) {
                assertThat(scalar(c, "select count(*) from application_settings"))
                        .isEqualTo("1");
                assertThat(scalar(c, "select tiny_topics_server_url from application_settings"))
                        .isEqualTo("http://www.tiny-topics.com:8088");
                statement.execute(
                        "update application_settings set tiny_topics_server_url='https://topics.example.test'");
            }
            migrate(ds);
            try (var c = ds.getConnection()) {
                assertThat(scalar(c, "select tiny_topics_server_url from application_settings"))
                        .isEqualTo("https://topics.example.test");
            }
        }
    }

    @Test
    void poiMigrationIsRepeatablePreservesChildrenAndEnforcesContainment() throws Exception {
        var ds = database();
        try (var c = ds.getConnection()) {
            legacy(c);
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var s = c.createStatement()) {
            s.execute("insert into welcome_centers(id,callsign) values(random_uuid(),'N1POI')");
            s.execute(
                    "insert into points_of_interest(id,welcome_center_id,name,latitude,longitude,symbol_code,symbol_table_id) select random_uuid(),id,'PARK','4336.50N','07258.30W','c','/' from welcome_centers");
            assertThat(scalar(c, "select temporarily_unavailable from points_of_interest"))
                    .isEqualTo("FALSE");
            assertThatThrownBy(() -> s.execute("delete from welcome_centers")).isInstanceOf(SQLException.class);
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select name from points_of_interest")).isEqualTo("PARK");
            assertThat(scalar(c, "select count(*) from aprs_object_name_lock")).isEqualTo("1");
        }
    }

    @Test
    void freshInstallDefaultsToOpenAndEnforcesStatus() throws Exception {
        var ds = database();
        migrate(ds);
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            s.execute("insert into welcome_centers(id, callsign) values(random_uuid(), 'N1FRESH')");
            assertThat(scalar(c, "select communication_mode from welcome_centers"))
                    .isEqualTo("ALL");
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
            assertThat(scalar(c, "select communication_mode from welcome_centers"))
                    .isEqualTo("ALL");
            s.execute("update welcome_centers set communication_mode='SELECTED'");
            s.execute("update welcome_centers set status='CLOSED'");
        }
        migrate(ds);
        try (Connection c = ds.getConnection()) {
            assertThat(scalar(c, "select status from welcome_centers")).isEqualTo("CLOSED");
            assertThat(scalar(c, "select count(*) from databasechangelog")).isEqualTo("28");
            assertThat(scalar(c, "select communication_mode from welcome_centers"))
                    .isEqualTo("SELECTED");
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
                assertThat(
                                scalar(
                                        c,
                                        "select count(*) from information_schema.columns where table_name='COMMUNICATION_INSTANCES' and column_name='TRANSMIT_ENABLED'"))
                        .isEqualTo("0");
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

    @Test
    void categoryMigrationAssignsTriggerCategoriesAndPreservesOnDemand() throws Exception {
        var ds = database();
        migrate(ds);
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            statement.execute(
                    "insert into welcome_centers(id,name,callsign) values('20000000-0000-0000-0000-000000000001','Test','N1ABC')");
            statement.execute(
                    "insert into communication_categories(id,name,auto_generated_text,system_defined) values('30000000-0000-0000-0000-000000000001','Custom',false,false)");
            for (String trigger : new String[] {
                "ENTER_REGION", "EXIT_REGION", "SCHEDULED_ONCE", "SCHEDULED_RECURRING", "SHRIEK_HEARD", "ON_REQUEST"
            }) {
                statement.execute(
                        "insert into communication_policies(id,welcome_center_id,category_id,message_text,communication_event_type) values(random_uuid(),'20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','Preserve text','"
                                + trigger + "')");
            }
            statement.execute(
                    "insert into communication_policies(id,welcome_center_id,category_id,message_text,communication_event_type) select random_uuid(),'20000000-0000-0000-0000-000000000001',id,'Legacy on demand','ON_REQUEST' from communication_categories where name in ('Welcome','Goodbye')");
            for (int run = 0; run < 2; run++) {
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/changelog/025-policy-categories.sql"));
                assertThat(
                                scalar(
                                        c,
                                        "select count(*) from communication_policies p join communication_categories c on p.category_id=c.id where p.communication_event_type='ON_REQUEST' and c.name in ('Welcome','Goodbye') and p.version=0"))
                        .isEqualTo("2");
                assertThat(
                                scalar(
                                        c,
                                        "select count(*) from communication_policies p join communication_categories c on p.category_id=c.id where (p.communication_event_type='ENTER_REGION' and c.name='Welcome') or (p.communication_event_type='EXIT_REGION' and c.name='Goodbye') or (p.communication_event_type='ON_REQUEST' and c.name='Custom') or (p.communication_event_type in ('SCHEDULED_ONCE','SCHEDULED_RECURRING','SHRIEK_HEARD') and c.name='General')"))
                        .isEqualTo("6");
                assertThat(scalar(c, "select count(*) from communication_policies where message_text='Preserve text'"))
                        .isEqualTo("6");
                assertThat(scalar(c, "select sum(version) from communication_policies"))
                        .isEqualTo("5");
            }
        }
    }

    @Test
    void categoryCommandsMigrateExistingRowsAndRemainStableOnRestart() throws Exception {
        var ds = database();
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            legacy(c);
            for (String name : new String[] {"Amateur Radio", "Clubs", "Events", "Commercial Radio"})
                statement.execute(
                        "insert into communication_categories(id,name,description,auto_generated_text,system_defined) values(random_uuid(),'"
                                + name + "','Preserved',false,true)");
        }
        migrate(ds);
        migrate(ds);
        try (var c = ds.getConnection()) {
            for (String[] pair : new String[][] {
                {"Amateur Radio", "VOICE"}, {"Clubs", "CLUBS"}, {"Events", "EVENTS"}, {"Commercial Radio", "COMM"}
            })
                assertThat(scalar(
                                c,
                                "select associated_command from communication_categories where name='" + pair[0] + "'"))
                        .isEqualTo(pair[1]);
            assertThat(scalar(c, "select count(*) from communication_categories where description='Preserved'"))
                    .isEqualTo("4");
            assertThat(
                            scalar(
                                    c,
                                    "select count(*) from communication_categories where name='General' and associated_command is null"))
                    .isEqualTo("1");
        }
    }

    @Test
    void warningsMigrationPreservesExistingCategoryAndPolicies() throws Exception {
        var ds = database();
        String category = UUID.randomUUID().toString();
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            legacy(c);
            statement.execute(
                    "insert into communication_categories(id,name,description,auto_generated_text,system_defined) values('"
                            + category + "','Warnings','Existing warnings',false,false)");
            statement.execute(
                    "insert into welcome_centers(id,name,callsign) values('20000000-0000-0000-0000-000000000001','Test','N1ABC')");
            statement.execute(
                    "insert into communication_policies(id,category_id,welcome_center_id,message_text,communication_event_type) values(random_uuid(),'"
                            + category + "','20000000-0000-0000-0000-000000000001','Keep this warning','ON_REQUEST')");
        }
        migrate(ds);
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(scalar(c, "select associated_command from communication_categories where id='" + category + "'"))
                    .isEqualTo("WARNINGS");
            assertThat(scalar(c, "select count(*) from communication_categories where name='Warnings'"))
                    .isEqualTo("1");
            assertThat(scalar(
                            c, "select message_text from communication_policies where category_id='" + category + "'"))
                    .isEqualTo("Keep this warning");
        }
    }

    @Test
    void scheduledTransmissionMigrationDefaultsExistingPoliciesAndPreservesEdits() throws Exception {
        var ds = database();
        try (var connection = ds.getConnection();
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            migration.update(27, "");
        }
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            statement.execute(
                    "insert into welcome_centers(id,name,callsign) values('20000000-0000-0000-0000-000000000001','Test','N1ABC')");
            for (String type : new String[] {"SCHEDULED_ONCE", "SCHEDULED_RECURRING", "ON_REQUEST"})
                statement.execute(
                        "insert into communication_policies(id,welcome_center_id,category_id,communication_event_type,message_type,message_text) select random_uuid(),'20000000-0000-0000-0000-000000000001',id,'"
                                + type + "','MESSAGE','Original' from communication_categories where name='General'");
        }
        migrate(ds);
        try (var c = ds.getConnection();
                var statement = c.createStatement()) {
            assertThat(
                            scalar(
                                    c,
                                    "select count(*) from communication_policies where message_type='BULLETIN' and bulletin_address='BLN1'"))
                    .isEqualTo("2");
            assertThat(
                            scalar(
                                    c,
                                    "select message_type from communication_policies where communication_event_type='ON_REQUEST'"))
                    .isEqualTo("MESSAGE");
            statement.execute(
                    "update communication_policies set message_type='STATUS_REPORT',bulletin_address=null where communication_event_type='SCHEDULED_ONCE'");
        }
        migrate(ds);
        try (var c = ds.getConnection()) {
            assertThat(
                            scalar(
                                    c,
                                    "select message_type from communication_policies where communication_event_type='SCHEDULED_ONCE'"))
                    .isEqualTo("STATUS_REPORT");
        }
    }
}
