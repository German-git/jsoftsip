package com.jsoftsip.core.history;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.infrastructure.async.KeyedSerialExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultHistoryService implements HistoryService {

    private final CallHistoryRepository repository;

    private final KeyedSerialExecutor executor = new KeyedSerialExecutor();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public DefaultHistoryService(CallHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void registerFinishedCall(CallLeg call) {

        CallHistoryEntry entry = buildEntry(call);

        executor.submit(entry.getAccountId(), () -> {

            repository.save(entry);

            notifyListeners();
        });
    }

    private static CallHistoryEntry buildEntry(CallLeg call) {

        SipAccount account = call.getAccount();

        // Invariant: both production creation
        // paths resolve the account orThrow at registration time,
        // and the schema pins account_id NOT NULL, so a finished
        // leg without an account means a lifecycle bypass - fail
        // fast here instead of dying later as an unboxing NPE
        // inside the repository.
        if (account == null) {

            throw new IllegalStateException("Finished call has no account: " + call.getBackendCallId());
        }

        CallHistoryEntry entry = new CallHistoryEntry();

        entry.setAccountId(account.getId());

        entry.setAccountUsername(account.getUsername() != null ? account.getUsername() : "unknown");

        entry.setDestination(call.getDestination());

        entry.setDirection(call.getDirection());

        LocalDateTime startedAt = call.getStartedAt() != null ? call.getStartedAt() : call.getEndedAt();
        entry.setStartedAt(startedAt);

        entry.setEndedAt(call.getEndedAt());

        entry.setDurationSeconds(call.getDurationSeconds());

        entry.setResult(call.getResult() != null ? call.getResult() : call.resolveResult());

        return entry;
    }

    @Override
    public List<CallHistoryEntry> getHistory() {

        return repository.findAll();
    }

    @Override
    public void clearAll() {

        repository.deleteAll();

        notifyListeners();
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {

        executor.close();
    }

    /**
     * Test hook: blocks until every queued history write has
     * been persisted.
     */
    void awaitPersisted() {

        executor.awaitIdle();
    }

    private void notifyListeners() {

        listeners.forEach(Runnable::run);
    }
}