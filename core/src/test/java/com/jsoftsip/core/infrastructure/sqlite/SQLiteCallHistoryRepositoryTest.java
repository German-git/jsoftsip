package com.jsoftsip.core.infrastructure.sqlite;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.core.history.CallHistoryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteCallHistoryRepositoryTest {

    @TempDir
    Path tempDir;

    private SQLiteCallHistoryRepository repository;

    private long accountId;

    @BeforeEach
    void setUp() {

        DatabaseManager.useDatabaseFile(tempDir.resolve("test.db"));

        DatabaseInitializer.migrate();

        SipAccount account = new SipAccount();

        account.setDisplayName("Ana López");
        account.setUsername("ana");
        account.setPassword("s3cret-ana");
        account.setDomain("sip.example.org");
        account.setTransport(SipTransport.TLS);
        account.setStatus(AccountStatus.ONLINE);

        accountId = new SQLiteAccountRepository().save(account).getId();

        repository = new SQLiteCallHistoryRepository();
    }

    @AfterEach
    void tearDown() {

        DatabaseManager.resetDatabaseFile();
    }

    private CallHistoryEntry entry(String destination, LocalDateTime startedAt) {

        CallHistoryEntry entry = new CallHistoryEntry();

        entry.setAccountId(accountId);
        entry.setAccountUsername("ana");
        entry.setDestination(destination);
        entry.setDirection(CallDirection.OUTGOING);
        entry.setStartedAt(startedAt);
        entry.setEndedAt(startedAt != null ? startedAt.plusMinutes(2) : null);
        entry.setDurationSeconds(120);
        entry.setResult(CallResult.ANSWERED);

        return entry;
    }

    @Test
    void saveAndFindAllRoundTripEntry() {

        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 17, 10, 30);

        repository.save(entry("1001", startedAt));

        List<CallHistoryEntry> entries = repository.findAll();

        assertEquals(1, entries.size());

        CallHistoryEntry found = entries.get(0);

        assertTrue(found.getId() > 0);
        assertEquals(accountId, found.getAccountId());
        assertEquals("ana", found.getAccountUsername());
        assertEquals("1001", found.getDestination());
        assertEquals(CallDirection.OUTGOING, found.getDirection());
        assertEquals(startedAt, found.getStartedAt());
        assertEquals(startedAt.plusMinutes(2), found.getEndedAt());
        assertEquals(120, found.getDurationSeconds());
        assertEquals(CallResult.ANSWERED, found.getResult());
    }

    @Test
    void saveAllowsNullTimestamps() {

        repository.save(entry("1002", null));

        List<CallHistoryEntry> entries = repository.findAll();

        assertEquals(1, entries.size());
        assertNull(entries.get(0).getStartedAt());
        assertNull(entries.get(0).getEndedAt());
    }

    @Test
    void findAllOrdersByStartedAtDescending() {

        LocalDateTime earlier = LocalDateTime.of(2026, 8, 16, 9, 0);

        LocalDateTime later = LocalDateTime.of(2026, 8, 17, 9, 0);

        repository.save(entry("first", earlier));
        repository.save(entry("second", later));

        List<CallHistoryEntry> entries = repository.findAll();

        assertEquals(2, entries.size());
        assertEquals("second", entries.get(0).getDestination());
        assertEquals("first", entries.get(1).getDestination());
    }

    @Test
    void deleteAllClearsEntriesAndResetsSequence() {

        repository.save(entry("1001", LocalDateTime.now()));
        repository.save(entry("1002", LocalDateTime.now()));

        repository.deleteAll();

        assertTrue(repository.findAll().isEmpty());

        repository.save(entry("1003", LocalDateTime.now()));

        List<CallHistoryEntry> entries = repository.findAll();

        assertEquals(1, entries.size());
        assertEquals(1L, entries.get(0).getId(), "sequence must reset so new history starts at 1");
    }
}