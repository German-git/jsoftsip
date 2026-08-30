package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.core.exception.RepositoryException;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.CallHistoryRepository;
import com.jsoftsip.core.logging.JSoftSipLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SQLiteCallHistoryRepository implements CallHistoryRepository {

    @Override
    public void save(CallHistoryEntry entry) {

        String sql = """
            INSERT INTO call_history
            (
                account_id,
                account_username,
                destination,
                direction,
                started_at,
                ended_at,
                duration_seconds,
                result
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, entry.getAccountId());

            statement.setString(2, entry.getAccountUsername());

            statement.setString(3, entry.getDestination());

            statement.setString(4, entry.getDirection().name());

            statement.setString(5, entry.getStartedAt() != null ? entry.getStartedAt().toString() : null);

            statement.setString(6, entry.getEndedAt() != null ? entry.getEndedAt().toString() : null);

            statement.setLong(7, entry.getDurationSeconds());

            statement.setString(8, entry.getResult() != null ? entry.getResult().name() : null);

            statement.executeUpdate();

        } catch (Exception exception) {

            String message = "Failed to save call history for account " + entry.getAccountId();

            // DEBUG, not ERROR (docs/exceptions.md rule 3): the
            // async executor that runs this task already logs the
            // failure at ERROR when the rethrown RepositoryException
            // reaches it - logging both levels would double-report.
            JSoftSipLog.debug(message + ": " + exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public void deleteAll() {

        String deleteHistory = "DELETE FROM call_history";

        String resetSequence = "DELETE FROM sqlite_sequence WHERE name = 'call_history'";

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement deleteStmt = connection.prepareStatement(deleteHistory);

            PreparedStatement resetStmt = connection.prepareStatement(resetSequence)) {

            connection.setAutoCommit(false);

            try {

                deleteStmt.executeUpdate();
                resetStmt.executeUpdate();

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;
            }

        } catch (Exception exception) {

            String message = "Failed to delete call history";

            // DEBUG, not ERROR (docs/exceptions.md rule 3): the
            // caller runs on the ui executor whose uncaught-exception
            // handler logs at ERROR - keep a single report.
            JSoftSipLog.debug(message + ": " + exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public List<CallHistoryEntry> findAll() {

        String sql = "SELECT * FROM call_history ORDER BY started_at DESC";

        List<CallHistoryEntry> result = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql);

            ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {

                CallHistoryEntry entry = new CallHistoryEntry();

                entry.setId(rs.getLong("id"));

                entry.setAccountId(rs.getLong("account_id"));

                entry.setAccountUsername(rs.getString("account_username"));

                entry.setDestination(rs.getString("destination"));

                entry.setDurationSeconds(rs.getLong("duration_seconds"));

                entry.setDirection(CallDirection.valueOf(rs.getString("direction")));

                entry.setResult(CallResult.valueOf(rs.getString("result")));

                String startedAt = rs.getString("started_at");

                if (startedAt != null) {

                    entry.setStartedAt(LocalDateTime.parse(startedAt));
                }

                String endedAt = rs.getString("ended_at");

                if (endedAt != null) {

                    entry.setEndedAt(LocalDateTime.parse(endedAt));
                }

                result.add(entry);
            }

            return result;

        } catch (Exception exception) {

            String message = "Failed to load call history";

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }
}