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

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.precondition.CustomPrecondition;

/** Validates Hibernate-created databases before Liquibase adopts the frozen release schema. */
public class LegacySchemaPrecondition implements CustomPrecondition {
    @Override
    public void check(Database database) throws CustomPreconditionFailedException, CustomPreconditionErrorException {
        try (Connection expected = DriverManager.getConnection("jdbc:h2:mem:baseline_" + UUID.randomUUID());
                var input = getClass().getResourceAsStream("/db/changelog/001-legacy-schema.sql")) {
            if (input == null) throw new IllegalStateException("Missing baseline schema");
            for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank())
                    try (Statement statement = expected.createStatement()) {
                        statement.execute(sql);
                    }
            }
            Connection actual = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
            Set<String> tables = tables(expected);
            Set<String> actualTables = tables(actual);
            if (Collections.disjoint(tables, actualTables)) {
                // A fresh application database; unrelated tables and Liquibase history are left alone.
                return;
            }
            for (String table : tables) {
                if (!actualTables.contains(table)) fail("Missing table " + table);
                Map<String, Column> actualColumns = columns(actual, table);
                for (var entry : columns(expected, table).entrySet()) {
                    Column found = actualColumns.get(entry.getKey());
                    Column required = entry.getValue();
                    if (found == null
                            || found.type != required.type
                            || found.size < required.size
                            || (!required.nullable && found.nullable)) {
                        fail("Incompatible column " + table + "." + entry.getKey());
                    }
                }
                if (!keys(actual, table, true).containsAll(keys(expected, table, true)))
                    fail("Missing primary key on " + table);
                if (!keys(actual, table, false).containsAll(keys(expected, table, false)))
                    fail("Missing unique key on " + table);
                if (!foreignKeys(actual, table).containsAll(foreignKeys(expected, table)))
                    fail("Missing foreign key on " + table);
            }
        } catch (CustomPreconditionFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomPreconditionErrorException("Unable to verify the existing APRSWC schema", e);
        }
    }

    private static void fail(String detail) throws CustomPreconditionFailedException {
        throw new CustomPreconditionFailedException(
                detail
                        + ". Database was not baselined. Restore a backup or upgrade its schema to a supported APRSWC release first.");
    }

    private static Set<String> tables(Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rows = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
            while (rows.next()) result.add(rows.getString("TABLE_NAME"));
        }
        return result;
    }

    private record Column(int type, int size, boolean nullable) {}

    private static Map<String, Column> columns(Connection connection, String table) throws SQLException {
        Map<String, Column> result = new HashMap<>();
        try (ResultSet rows = connection.getMetaData().getColumns(null, "PUBLIC", table, "%")) {
            while (rows.next())
                result.put(
                        rows.getString("COLUMN_NAME"),
                        new Column(rows.getInt("DATA_TYPE"), rows.getInt("COLUMN_SIZE"), rows.getInt("NULLABLE") != 0));
        }
        return result;
    }

    private static Set<List<String>> keys(Connection connection, String table, boolean primary) throws SQLException {
        Map<String, TreeMap<Integer, String>> groups = new HashMap<>();
        try (ResultSet rows = primary
                ? connection.getMetaData().getPrimaryKeys(null, "PUBLIC", table)
                : connection.getMetaData().getIndexInfo(null, "PUBLIC", table, true, false)) {
            while (rows.next()) {
                String column = rows.getString("COLUMN_NAME");
                if (column == null) continue;
                groups.computeIfAbsent(rows.getString(primary ? "PK_NAME" : "INDEX_NAME"), key -> new TreeMap<>())
                        .put(rows.getInt(primary ? "KEY_SEQ" : "ORDINAL_POSITION"), column);
            }
        }
        Set<List<String>> result = new HashSet<>();
        groups.values().forEach(columns -> result.add(List.copyOf(columns.values())));
        return result;
    }

    private static Set<String> foreignKeys(Connection connection, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rows = connection.getMetaData().getImportedKeys(null, "PUBLIC", table)) {
            while (rows.next())
                result.add(rows.getString("FKCOLUMN_NAME") + ":" + rows.getString("PKTABLE_NAME") + ":"
                        + rows.getString("PKCOLUMN_NAME"));
        }
        return result;
    }
}
