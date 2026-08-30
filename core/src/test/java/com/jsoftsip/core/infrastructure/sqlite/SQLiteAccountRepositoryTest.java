package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.infrastructure.crypto.AesGcmEncryptionService;
import com.jsoftsip.core.infrastructure.crypto.MasterKeyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteAccountRepositoryTest {

    @TempDir
    Path tempDir;

    private SQLiteAccountRepository repository;

    @BeforeEach
    void setUp() {

        DatabaseManager.useDatabaseFile(tempDir.resolve("test.db"));

        DatabaseInitializer.migrate();

        repository = new SQLiteAccountRepository();
    }

    @AfterEach
    void tearDown() {

        DatabaseManager.resetDatabaseFile();
    }

    private SipAccount account(String username) {

        SipAccount account = new SipAccount();

        account.setDisplayName("Ana López");
        account.setUsername(username);
        account.setPassword("s3cret-" + username);
        account.setDomain("sip.example.org");
        account.setTransport(SipTransport.TLS);
        account.setStatus(AccountStatus.ONLINE);

        return account;
    }

    @Test
    void saveAssignsIdAndRoundTripsThroughEncryption() {

        SipAccount saved = repository.save(account("ana"));

        assertTrue(saved.getId() > 0);

        Optional<SipAccount> loaded = repository.findById(saved.getId());

        assertTrue(loaded.isPresent());

        SipAccount found = loaded.get();

        assertEquals(saved.getId(), found.getId());
        assertEquals("Ana López", found.getDisplayName());
        assertEquals("ana", found.getUsername());
        assertEquals("s3cret-ana", found.getPassword(), "password must be decrypted on read");
        assertEquals("sip.example.org", found.getDomain());
        assertEquals(SipTransport.TLS, found.getTransport());
        assertEquals(AccountStatus.ONLINE, found.getStatus());
    }

    @Test
    void passwordIsNotStoredInPlainText() throws Exception {

        SipAccount saved = repository.save(account("ana"));

        String rawPassword;

        try (Connection connection = DatabaseManager.getConnection();

            PreparedStatement statement = connection.prepareStatement("SELECT encrypted_password FROM accounts WHERE id = ?")) {

            statement.setLong(1, saved.getId());

            try (ResultSet resultSet = statement.executeQuery()) {

                resultSet.next();

                rawPassword = resultSet.getString("encrypted_password");
            }
        }

        assertNotEquals("s3cret-ana", rawPassword, "password must be encrypted at rest");
    }

    @Test
    void updatePersistsChanges() {

        SipAccount saved = repository.save(account("ana"));

        saved.setDisplayName("Ana Renombrada");
        saved.setStatus(AccountStatus.OFFLINE);

        repository.update(saved);

        Optional<SipAccount> loaded = repository.findById(saved.getId());

        assertTrue(loaded.isPresent());
        assertEquals("Ana Renombrada", loaded.get().getDisplayName());
        assertEquals(AccountStatus.OFFLINE, loaded.get().getStatus());
    }

    @Test
    void deleteRemovesAccount() {

        SipAccount saved = repository.save(account("ana"));

        repository.delete(saved.getId());

        assertTrue(repository.findById(saved.getId()).isEmpty());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void findAllReturnsAccountsInInsertionOrder() {

        repository.save(account("first"));
        repository.save(account("second"));
        repository.save(account("third"));

        List<SipAccount> accounts = repository.findAll();

        assertEquals(3, accounts.size());
        assertEquals("first", accounts.get(0).getUsername());
        assertEquals("second", accounts.get(1).getUsername());
        assertEquals("third", accounts.get(2).getUsername());
        assertFalse(accounts.get(0).getId().equals(accounts.get(2).getId()));
    }

    @Test
    void updateStatusChangesOnlyTheStatusColumn() {

        SipAccount saved = repository.save(account("ana"));

        repository.updateStatus(saved.getId(), AccountStatus.OFFLINE);

        SipAccount loaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(AccountStatus.OFFLINE, loaded.getStatus());
        assertEquals("Ana López", loaded.getDisplayName());
        assertEquals("s3cret-ana", loaded.getPassword());
    }

    @Test
    void deleteRemovesAssociatedHistoryInOneTransaction() {

        SipAccount saved = repository.save(account("ana"));

        CallHistoryEntry entry = new CallHistoryEntry();

        entry.setAccountId(saved.getId());
        entry.setAccountUsername("ana");
        entry.setDestination("1001");
        entry.setDirection(CallDirection.OUTGOING);
        entry.setDurationSeconds(30);
        entry.setResult(CallResult.ANSWERED);

        new SQLiteCallHistoryRepository().save(entry);

        repository.delete(saved.getId());

        assertTrue(repository.findById(saved.getId()).isEmpty());
        assertTrue(new SQLiteCallHistoryRepository().findAll().isEmpty(),
                   "deleting an account must remove its call history");
    }

    @Test
    void foreignKeyRejectsHistoryWithoutAccount() {

        CallHistoryEntry entry = new CallHistoryEntry();

        entry.setAccountId(999L);
        entry.setAccountUsername("ghost");
        entry.setDestination("1001");
        entry.setDirection(CallDirection.INCOMING);
        entry.setDurationSeconds(0);
        entry.setResult(CallResult.MISSED);

        SQLiteCallHistoryRepository historyRepository = new SQLiteCallHistoryRepository();

        assertThrows(com.jsoftsip.core.exception.RepositoryException.class, () -> historyRepository.save(entry));
    }

    @Test
    void rekeyCredentialsReEncryptsPasswordsUnderNewKey() throws Exception {

        SipAccount saved = repository.save(account("ana"));
        SipAccount saved2 = repository.save(account("bob"));

        SecretKey oldKey = MasterKeyManager.loadKey();
        SecretKey newKey = randomKey();

        String before = encryptedPassword(saved.getId());

        repository.rekeyCredentials(oldKey, newKey);

        String after = encryptedPassword(saved.getId());

        assertNotEquals(before, after, "the password must be re-encrypted under the new key");

        assertEquals("s3cret-ana", repository.findById(saved.getId()).orElseThrow().getPassword());
        assertEquals("s3cret-bob", repository.findById(saved2.getId()).orElseThrow().getPassword());

        AesGcmEncryptionService oldEnc = new AesGcmEncryptionService(oldKey);
        assertThrows(RuntimeException.class, () -> oldEnc.decrypt(after),
                     "the old key must fail to decrypt the re-encrypted value");
    }

    private String encryptedPassword(long id) throws Exception {

        try (Connection connection = DatabaseManager.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT encrypted_password FROM accounts WHERE id = ?")) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                resultSet.next();

                return resultSet.getString("encrypted_password");
            }
        }
    }

    private SecretKey randomKey() {

        byte[] key = new byte[32];

        new SecureRandom().nextBytes(key);

        return new SecretKeySpec(key, "AES");
    }
}