package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.account.AccountRepository;
import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.crypto.EncryptionService;
import com.jsoftsip.core.exception.RepositoryException;
import com.jsoftsip.core.infrastructure.crypto.AesGcmEncryptionService;
import com.jsoftsip.core.logging.JSoftSipLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;

public class SQLiteAccountRepository implements AccountRepository {

    private EncryptionService encryptionService;

    public SQLiteAccountRepository() {
        this.encryptionService = new AesGcmEncryptionService();
    }

    @Override
    public SipAccount save(SipAccount account) {

        String sql = """
            INSERT INTO accounts
            (
                display_name,
                username,
                encrypted_password,
                domain,
                transport,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, account.getDisplayName());

            statement.setString(2, account.getUsername());

            statement.setString(3, encryptionService.encrypt(account.getPassword()));

            statement.setString(4, account.getDomain());

            statement.setString(5, account.getTransport().name());

            statement.setString(6, account.getStatus().name());

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {

                if (generatedKeys.next()) {

                    account.setId(generatedKeys.getLong(1));
                }
            }

            return account;

        } catch (Exception exception) {

            String message = "Failed to save account " + account.getDisplayName();

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public SipAccount update(SipAccount account) {

        String sql = """
            UPDATE accounts
            SET
                display_name = ?,
                username = ?,
                encrypted_password = ?,
                domain = ?,
                transport = ?,
                status = ?
            WHERE id = ?
            """;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, account.getDisplayName());

            statement.setString(2, account.getUsername());

            statement.setString(3, encryptionService.encrypt(account.getPassword()));

            statement.setString(4, account.getDomain());

            statement.setString(5, account.getTransport().name());

            statement.setString(6, account.getStatus().name());

            statement.setLong(7, account.getId());

            statement.executeUpdate();

            return account;

        } catch (Exception exception) {

            String message = "Failed to update account " + account.getId();

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public void delete(long id) {

        String deleteHistory = "DELETE FROM call_history WHERE account_id = ?";

        String deleteAccount = "DELETE FROM accounts WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement historyStmt = connection.prepareStatement(deleteHistory);

            PreparedStatement accountStmt = connection.prepareStatement(deleteAccount)) {

            connection.setAutoCommit(false);

            try {

                historyStmt.setLong(1, id);

                historyStmt.executeUpdate();

                accountStmt.setLong(1, id);

                accountStmt.executeUpdate();

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;
            }

        } catch (Exception exception) {

            String message = "Failed to delete account " + id;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public void updateStatus(long id, AccountStatus status) {

        String sql = "UPDATE accounts SET status = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.name());

            statement.setLong(2, id);

            statement.executeUpdate();

        } catch (Exception exception) {

            String message = "Failed to update status of account " + id;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public Optional<SipAccount> findById(long id) {

        String sql = "SELECT * FROM accounts WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {

                    return Optional.of(mapRow(resultSet));
                }
            }

            return Optional.empty();

        } catch (Exception exception) {

            String message = "Failed to load account " + id;

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public List<SipAccount> findAll() {

        String sql = "SELECT * FROM accounts ORDER BY id";

        List<SipAccount> accounts = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement(sql);

            ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {

                accounts.add(mapRow(resultSet));
            }

            return accounts;

        } catch (Exception exception) {

            String message = "Failed to load accounts";

            JSoftSipLog.error(message, exception);

            throw new RepositoryException(message, exception);
        }
    }

    @Override
    public void rekeyCredentials(SecretKey oldKey, SecretKey newKey) throws SQLException {

        AesGcmEncryptionService oldEnc = new AesGcmEncryptionService(oldKey);

        AesGcmEncryptionService newEnc = new AesGcmEncryptionService(newKey);

        String selectSql = "SELECT id, encrypted_password FROM accounts";

        String updateSql = "UPDATE accounts SET encrypted_password = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection()) {

            connection.setAutoCommit(false);

            try (PreparedStatement select = connection.prepareStatement(selectSql);
                PreparedStatement update = connection.prepareStatement(updateSql)) {

                try (ResultSet resultSet = select.executeQuery()) {

                    while (resultSet.next()) {

                        long id = resultSet.getLong("id");

                        String encrypted = resultSet.getString("encrypted_password");

                        if (encrypted == null || encrypted.isBlank()) {

                            continue;
                        }

                        String plain = oldEnc.decrypt(encrypted);

                        String reencrypted = newEnc.encrypt(plain);

                        update.setString(1, reencrypted);
                        update.setLong(2, id);
                        update.addBatch();
                    }
                }

                update.executeBatch();

                connection.commit();

                this.encryptionService = newEnc;

            } catch (SQLException | IllegalStateException exception) {

                try {

                    connection.rollback();

                } catch (SQLException rollbackException) {

                    JSoftSipLog.error("Rollback failed during credential rekey", rollbackException);
                }

                if (exception instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException("Credential re-encryption failed during master key rotation", exception);
            } finally {

                connection.setAutoCommit(true);
            }
        }
    }

    private SipAccount mapRow(ResultSet resultSet) throws SQLException {

        SipAccount account = new SipAccount();

        account.setId(resultSet.getLong("id"));

        account.setDisplayName(resultSet.getString("display_name"));

        account.setUsername(resultSet.getString("username"));

        account.setPassword(encryptionService.decrypt(resultSet.getString("encrypted_password")));

        account.setDomain(resultSet.getString("domain"));

        account.setTransport(SipTransport.valueOf(resultSet.getString("transport")));

        account.setStatus(AccountStatus.valueOf(resultSet.getString("status")));

        return account;
    }
}