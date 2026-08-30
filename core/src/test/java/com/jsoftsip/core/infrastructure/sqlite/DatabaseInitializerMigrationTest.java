package com.jsoftsip.core.infrastructure.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInitializerMigrationTest {

    @TempDir
    Path tempDir;

    private Path databaseFile;

    @BeforeEach
    void setUp() {

        databaseFile = tempDir.resolve("legacy.db");
    }

    @AfterEach
    void tearDown() {

        DatabaseManager.resetDatabaseFile();
    }

    private int readUserVersion() throws Exception {

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);

            Statement statement = connection.createStatement();

            ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {

            resultSet.next();

            return resultSet.getInt(1);
        }
    }

    private boolean tableExists(String tableName) throws Exception {

        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?";

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);

            java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    @Test
    void emptyDatabaseMigratesToCurrentSchema() throws Exception {

        DatabaseManager.useDatabaseFile(databaseFile);

        DatabaseInitializer.migrate();

        assertEquals(1, readUserVersion());
        assertTrue(tableExists("accounts"));
        assertTrue(tableExists("call_history"));
        assertTrue(tableExists("settings"));
    }

    @Test
    void legacyVersionZeroDatabaseKeepsDataAndGetsMigrated() throws Exception {

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);

            Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE accounts
                (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    display_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    encrypted_password TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    transport TEXT NOT NULL,
                    status TEXT NOT NULL
                )
                """);

            statement.execute("""
                INSERT INTO accounts (display_name, username, encrypted_password, domain, transport, status)
                VALUES ('Legacy', 'legacy-user', 'cipher', 'sip.example.org', 'UDP', 'OFFLINE')
                """);
        }

        DatabaseManager.useDatabaseFile(databaseFile);

        DatabaseInitializer.migrate();

        assertEquals(1, readUserVersion());
        assertTrue(tableExists("call_history"));
        assertTrue(tableExists("settings"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);

            Statement statement = connection.createStatement();

            ResultSet resultSet = statement.executeQuery("SELECT username FROM accounts WHERE username = 'legacy-user'")) {

            assertTrue(resultSet.next(), "existing data must survive migration");
        }
    }

    @Test
    void migrateIsIdempotent() throws Exception {

        DatabaseManager.useDatabaseFile(databaseFile);

        DatabaseInitializer.migrate();
        DatabaseInitializer.migrate();

        assertEquals(1, readUserVersion());
    }
}