package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.config.ApplicationPaths;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private static Path databaseFileOverride;

    private DatabaseManager() {
    }

    static void useDatabaseFile(Path databaseFile) {

        databaseFileOverride = databaseFile;
    }

    static void resetDatabaseFile() {

        databaseFileOverride = null;
    }

    public static Connection getConnection() throws SQLException {

        Path databaseFile = databaseFileOverride != null ? databaseFileOverride : ApplicationPaths.getDatabaseFile();

        String jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();

        Connection connection = DriverManager.getConnection(jdbcUrl);

        try (Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");

            statement.execute("PRAGMA busy_timeout = 5000");
        }

        return connection;
    }
}