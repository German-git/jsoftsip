package com.jsoftsip.core.infrastructure.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the shared connection factory:
 * the PRAGMA hardening applied to every connection is part of
 * the data-integrity contract of the persistence layer.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void connectionUsesTheOverrideFileAndAppliesTheHardeningPragmas() throws SQLException {

        Path databaseFile = tempDir.resolve("override.db");

        DatabaseManager.useDatabaseFile(databaseFile);

        try {

            Connection connection = DatabaseManager.getConnection();

            try (Statement statement = connection.createStatement()) {

                // Force sqlite to materialize the file, then verify both pragmas
                statement.execute("create table probe (id integer primary key)");

                try (ResultSet foreignKeys = statement.executeQuery("pragma foreign_keys")) {

                    assertTrue(foreignKeys.next(), "pragma foreign_keys must answer");

                    assertEquals(1, foreignKeys.getInt(1), "foreign key enforcement must be ON");
                }

                try (ResultSet busyTimeout = statement.executeQuery("pragma busy_timeout")) {

                    assertTrue(busyTimeout.next(), "pragma busy_timeout must answer");

                    assertEquals(5000, busyTimeout.getInt(1), "the busy timeout must be 5000 ms");
                }
            }

            connection.close();

            assertTrue(Files.exists(databaseFile), "connections must target the overridden file");

        } finally {

            DatabaseManager.resetDatabaseFile();
        }
    }
}
