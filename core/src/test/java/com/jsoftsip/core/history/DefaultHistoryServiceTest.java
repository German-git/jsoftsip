package com.jsoftsip.core.history;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.core.call.CallState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultHistoryServiceTest {

    private final RecordingCallHistoryRepository repository = new RecordingCallHistoryRepository();

    private final DefaultHistoryService service = new DefaultHistoryService(repository);

    @AfterEach
    void tearDown() {

        service.close();
    }

    private CallLeg finishedCall(long accountId, String destination) {

        SipAccount account = new SipAccount();

        account.setId(accountId);
        account.setDisplayName("Ana López");
        account.setUsername("ana");
        account.setPassword("s3cret");
        account.setDomain("sip.example.org");
        account.setTransport(SipTransport.TLS);
        account.setStatus(AccountStatus.ONLINE);

        CallLeg call = new CallLeg();

        call.setAccount(account);
        call.setDestination(destination);
        call.setDirection(CallDirection.OUTGOING);
        call.setState(CallState.ENDED);
        call.setResult(CallResult.ANSWERED);

        return call;
    }

    @Test
    void registerFinishedCallReturnsWithoutWaitingForPersistence() throws Exception {

        CountDownLatch saveStarted = new CountDownLatch(1);

        CountDownLatch releaseSave = new CountDownLatch(1);

        repository.blockSaveForAccount(1, saveStarted, releaseSave);

        AtomicBoolean returned = new AtomicBoolean();

        Thread caller = new Thread(() -> {

            service.registerFinishedCall(finishedCall(1, "1001"));

            returned.set(true);
        });

        caller.start();

        assertTrue(saveStarted.await(2, TimeUnit.SECONDS), "the persistence task must start");

        assertTrue(returned.get(), "the caller must not wait for the DB write");

        releaseSave.countDown();

        caller.join(2000);

        service.awaitPersisted();

        assertTrue(returned.get());
        assertEquals(1, repository.savedEntries().size());
    }

    @Test
    void entriesArePersistedInSubmissionOrderPerAccount() {

        for (int i = 1; i <= 5; i++) {

            service.registerFinishedCall(finishedCall(7, "dest-" + i));
        }

        service.awaitPersisted();

        List<String> destinations = repository.savedEntries().stream().map(CallHistoryEntry::getDestination).toList();

        assertEquals(List.of("dest-1", "dest-2", "dest-3", "dest-4", "dest-5"), destinations,
                     "saves for the same account must run in submission order");
    }

    @Test
    void blockedAccountDoesNotStallAnotherAccount() throws Exception {

        CountDownLatch firstStarted = new CountDownLatch(1);

        CountDownLatch releaseFirst = new CountDownLatch(1);

        repository.blockSaveForAccount(1, firstStarted, releaseFirst);

        CountDownLatch secondStarted = new CountDownLatch(1);

        CountDownLatch releaseSecond = new CountDownLatch(1);

        repository.blockSaveForAccount(2, secondStarted, releaseSecond);

        service.registerFinishedCall(finishedCall(1, "slow-account"));
        service.registerFinishedCall(finishedCall(2, "fast-account"));

        assertTrue(firstStarted.await(2, TimeUnit.SECONDS), "the blocked save must start");

        assertTrue(secondStarted.await(2, TimeUnit.SECONDS),
                   "the other account must start its save while the first is blocked");

        releaseFirst.countDown();
        releaseSecond.countDown();

        service.awaitPersisted();

        assertEquals(2, repository.savedEntries().size());
    }

    @Test
    void listenersAreNotifiedAfterPersistence() throws Exception {

        CountDownLatch notified = new CountDownLatch(1);

        service.addListener(notified::countDown);

        service.registerFinishedCall(finishedCall(1, "1001"));

        assertTrue(notified.await(2, TimeUnit.SECONDS), "listeners must be notified once the entry is saved");
    }

    @Test
    void getHistoryDelegatesToRepository() {

        service.registerFinishedCall(finishedCall(1, "1001"));

        service.awaitPersisted();

        assertEquals(repository.savedEntries().size(), service.getHistory().size());
    }

    @Test
    void rejectsAFinishedCallWithoutAnAccountInsteadOfLosingItSilently() {

        // The schema pins account_id NOT NULL, so
        // a leg without an account can never be persisted. A lifecycle
        // bypass must fail fast at registration instead of surfacing
        // later as an unboxing NPE inside the repository.
        CallLeg orphan = finishedCall(1, "orphan");

        orphan.setAccount(null);

        assertThrows(IllegalStateException.class, () -> service.registerFinishedCall(orphan),
                     "a finished call without an account must be rejected at registration time");
    }

    private static final class RecordingCallHistoryRepository implements CallHistoryRepository {

        private final List<CallHistoryEntry> saved = new CopyOnWriteArrayList<>();

        private final Map<Long, CountDownLatch[]> blockers = new ConcurrentHashMap<>();

        void blockSaveForAccount(long accountId, CountDownLatch started, CountDownLatch release) {

            blockers.put(accountId, new CountDownLatch[]{started, release});
        }

        @Override
        public void save(CallHistoryEntry entry) {

            CountDownLatch[] blocker = blockers.get(entry.getAccountId());

            if (blocker != null) {

                blocker[0].countDown();

                try {

                    blocker[1].await(5, TimeUnit.SECONDS);

                } catch (InterruptedException exception) {

                    Thread.currentThread().interrupt();
                }
            }

            saved.add(entry);
        }

        @Override
        public void deleteAll() {

            saved.clear();
        }

        @Override
        public List<CallHistoryEntry> findAll() {

            return List.copyOf(saved);
        }

        List<CallHistoryEntry> savedEntries() {

            return saved;
        }
    }
}