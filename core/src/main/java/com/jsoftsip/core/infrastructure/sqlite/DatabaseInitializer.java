package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.config.ApplicationPaths;
import com.jsoftsip.core.logging.JSoftSipLog;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public final class DatabaseInitializer {

    private static final int SCHEMA_VERSION = 1;

    private DatabaseInitializer() {
    }

    public static void initialize() {

        createConfigurationDirectory();
        migrate();
    }

    static void migrate() {

        try (Connection connection = DatabaseManager.getConnection()) {

            int currentVersion = readUserVersion(connection);

            if (currentVersion < 1) {

                createVersion1Schema(connection);

                writeUserVersion(connection, 1);
            }

        } catch (Exception exception) {

            JSoftSipLog.error("Failed to migrate database", exception);

            throw new IllegalStateException("Failed to migrate database", exception);
        }
    }

    private static void createConfigurationDirectory() {

        try {

            Files.createDirectories(ApplicationPaths.getConfigDirectory());

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to create configuration directory", exception);

            throw new IllegalStateException("Failed to create configuration directory", exception);
        }
    }

    private static int readUserVersion(Connection connection) throws Exception {

        try (Statement statement = connection.createStatement();

            ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {

            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static void writeUserVersion(Connection connection, int version) throws Exception {

        try (Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA user_version = " + version);
        }
    }

    private static void createVersion1Schema(Connection connection) throws Exception {

        try (Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS accounts
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
                CREATE TABLE IF NOT EXISTS call_history
                (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,

                    account_id INTEGER NOT NULL,

                    account_username TEXT NOT NULL,

                    destination TEXT NOT NULL,

                    direction TEXT NOT NULL,

                    started_at TEXT,

                    ended_at TEXT,

                    duration_seconds INTEGER NOT NULL,

                    result TEXT NOT NULL,

                    FOREIGN KEY(account_id)
                        REFERENCES accounts(id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS settings
                (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,

                    setting_key TEXT NOT NULL UNIQUE,

                    setting_value TEXT NOT NULL
                )
                """);
        }
    }
}