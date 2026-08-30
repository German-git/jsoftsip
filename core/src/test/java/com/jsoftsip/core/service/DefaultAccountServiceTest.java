package com.jsoftsip.core.service;

import com.jsoftsip.core.account.AccountRepository;
import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.config.ApplicationPaths;
import com.jsoftsip.core.config.ConfigDirectoryResolver;
import com.jsoftsip.core.exception.RepositoryException;
import com.jsoftsip.core.infrastructure.crypto.MasterKeyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutates the global configuration directory system property, so it is
 * forced to run on a single thread to avoid interfering with other tests
 * that may read it in parallel.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DefaultAccountServiceTest {

    @TempDir
    Path tempConfig;

    private String originalConfigDir;

    private InMemoryAccountRepository repository;

    private DefaultAccountService service;

    @BeforeEach
    void setUp() {

        originalConfigDir = System.getProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, tempConfig.toString());

        repository = new InMemoryAccountRepository();

        service = new DefaultAccountService(repository);
    }

    @AfterEach
    void tearDown() {

        if (originalConfigDir == null) {

            System.clearProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        } else {

            System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, originalConfigDir);
        }
    }

    @Test
    void createAccountPersistsAndReturnsGeneratedId() {
        SipAccount account = account("1001", "demo.org");

        SipAccount saved = service.createAccount(account);

        assertTrue(saved.getId() > 0, "createAccount must assign an id");
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void updateAccountChangesPersistedFields() {
        SipAccount saved = service.createAccount(account("1001", "demo.org"));
        saved.setUsername("2002");

        service.updateAccount(saved);

        assertEquals("2002", service.findById(saved.getId()).orElseThrow().getUsername());
    }

    @Test
    void deleteAccountRemovesItFromRepository() {
        SipAccount saved = service.createAccount(account("1001", "demo.org"));

        service.deleteAccount(saved.getId());

        assertTrue(service.findById(saved.getId()).isEmpty());
        assertTrue(service.getAccounts().isEmpty());
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertTrue(service.findById(999L).isEmpty());
    }

    @Test
    void updateStatusNotifiesRegisteredListeners() {
        SipAccount saved = service.createAccount(account("1001", "demo.org"));
        AtomicLong notified = new AtomicLong(-1);
        service.addListener(notified::set);

        service.updateStatus(saved.getId(), AccountStatus.ONLINE);

        assertEquals(saved.getId(), notified.get());
    }

    @Test
    void rotateMasterKeyLeavesActiveKeyUntouchedWhenRekeyFails() throws IOException {

        MasterKeyManager.initialize();
        byte[] originalKeyBytes = Files.readAllBytes(ApplicationPaths.getMasterKeyFile());

        repository.failNextRekeyWith(new SQLException("rekey failed"));

        assertThrows(RepositoryException.class, () -> service.rotateMasterKey());

        assertArrayEquals(originalKeyBytes, Files.readAllBytes(ApplicationPaths.getMasterKeyFile()),
                          "the active key file must remain unchanged when re-encryption fails");
        assertTrue(Files.exists(ApplicationPaths.getMasterKeyBackupFile()),
                   "the backup must survive a failed rotation");
        assertFalse(Files.exists(tempConfig.resolve("master.key.staged")),
                    "a failed rotation must leave no staged key behind");
    }

    @Test
    void rotateMasterKeyRekeysUnderLoadedAndPreparedKeysThenPromotesTheNewKey() throws IOException {

        MasterKeyManager.initialize();
        byte[] originalKeyBytes = Files.readAllBytes(ApplicationPaths.getMasterKeyFile());

        service.rotateMasterKey();

        assertNotNull(repository.getRekeyedOldKey(), "the repository must receive the old key");
        assertArrayEquals(originalKeyBytes, repository.getRekeyedOldKey().getEncoded(),
                          "re-encryption must use the loaded old key");
        SecretKey preparedNewKey = repository.getRekeyedNewKey();
        assertNotNull(preparedNewKey, "the repository must receive the new key");
        assertFalse(preparedNewKey.equals(repository.getRekeyedOldKey()),
                    "the prepared key must differ from the old key");
        assertArrayEquals(preparedNewKey.getEncoded(), Files.readAllBytes(ApplicationPaths.getMasterKeyFile()),
                          "commit must promote the new key over the active key file");
        assertFalse(Files.exists(tempConfig.resolve("master.key.staged")),
                    "a committed rotation must leave no staged key behind");
        assertFalse(Files.exists(ApplicationPaths.getMasterKeyBackupFile()),
                    "commit must delete the backup so the old key is discarded");
    }

    private SipAccount account(String username, String domain) {
        SipAccount account = new SipAccount();
        account.setUsername(username);
        account.setDomain(domain);
        account.setStatus(AccountStatus.OFFLINE);
        return account;
    }

    static class InMemoryAccountRepository implements AccountRepository {

        private final Map<Long, SipAccount> store = new ConcurrentHashMap<>();

        private final AtomicLong sequence = new AtomicLong(1);

        private SQLException rekeyFailure;

        private SecretKey rekeyedOldKey;

        private SecretKey rekeyedNewKey;

        void failNextRekeyWith(SQLException exception) {

            this.rekeyFailure = exception;
        }

        SecretKey getRekeyedOldKey() {

            return rekeyedOldKey;
        }

        SecretKey getRekeyedNewKey() {

            return rekeyedNewKey;
        }

        @Override
        public SipAccount save(SipAccount account) {
            SipAccount copy = copy(account);
            Long current = copy.getId();
            long id = (current == null || current == 0L) ? sequence.getAndIncrement() : current;
            copy.setId(id);
            store.put(id, copy);
            return copy;
        }

        @Override
        public SipAccount update(SipAccount account) {
            SipAccount copy = copy(account);
            store.put(copy.getId(), copy);
            return copy;
        }

        @Override
        public void delete(long id) {
            store.remove(id);
        }

        @Override
        public void updateStatus(long id, AccountStatus status) {
            SipAccount account = store.get(id);
            if (account != null) {
                account.setStatus(status);
            }
        }

        @Override
        public Optional<SipAccount> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<SipAccount> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public void rekeyCredentials(SecretKey oldKey, SecretKey newKey) throws SQLException {

            this.rekeyedOldKey = oldKey;

            this.rekeyedNewKey = newKey;

            if (rekeyFailure != null) {

                throw rekeyFailure;
            }
        }

        private SipAccount copy(SipAccount source) {
            SipAccount copy = new SipAccount();
            copy.setId(source.getId());
            copy.setUsername(source.getUsername());
            copy.setDomain(source.getDomain());
            copy.setPassword(source.getPassword());
            copy.setDisplayName(source.getDisplayName());
            copy.setTransport(source.getTransport());
            copy.setStatus(source.getStatus());
            return copy;
        }
    }
}
