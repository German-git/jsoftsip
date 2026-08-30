package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.exception.RepositoryException;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.settings.SettingRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class SQLiteSettingRepository implements SettingRepository {

    @Override
    public void save(String key, String value) {

        String sql = """
            INSERT OR REPLACE INTO settings
            (
                setting_key,
                setting_value
            )
            VALUES (?, ?)
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, key);

            statement.setString(2, value);

            statement.executeUpdate();

        } catch (Exception exception) {

            String message = "Failed to save setting " + key;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public void delete(String key) {

        String sql = """
            DELETE FROM settings
            WHERE setting_key = ?
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, key);

            statement.executeUpdate();

        } catch (Exception exception) {

            String message = "Failed to delete setting " + key;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public Optional<String> findValue(String key) {

        String sql = """
            SELECT setting_value
            FROM settings
            WHERE setting_key = ?
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, key);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {

                    return Optional.ofNullable(resultSet.getString("setting_value"));
                }
            }

            return Optional.empty();

        } catch (Exception exception) {

            String message = "Failed to load setting " + key;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }
}