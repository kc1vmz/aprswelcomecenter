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
            assertThat(scalar(c, "select count(*) from databasechangelog")).isEqualTo("3");
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
