package com.jsoftsip.core.registration;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.infrastructure.async.KeyedSerialExecutor;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipRegistrationState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultRegistrationService implements RegistrationService, SipEventListener {

    private final SipClient sipClient;

    private final AccountService accountService;

    private final SettingsService settingsService;

    private final List<RegistrationListener> registrationListeners = new CopyOnWriteArrayList<>();

    /**
     * Ids with a live registration intent: REGISTERING counts
     * because uanew was already sent and a restart landing
     * mid-registration must not lose the account. Terminal
     * states (UNREGISTERED, FAILED) drop the id so session
     * recovery never resurrects them.
     */
    private final Set<Long> registeredAccountIds = ConcurrentHashMap.newKeySet();

    /**
     * Accounts waiting for their UA teardown to land. When the
     * UNREGISTERED event for the account arrives, the stored
     * account is re-registered with its new SIP fields.
     */
    private final Map<Long, SipAccount> pendingReprovision = new ConcurrentHashMap<>();

    /**
     * Per-account registration timeout timers. When uanew is
     * sent, a timer starts. On REGISTERED or FAILED the timer
     * is cancelled. If the timer fires before any event, a
     * synthetic FAILED event is emitted and uadel is sent.
     */
    private final Map<Long, ScheduledFuture<?>> registrationTimers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reg-timeout");
        t.setDaemon(true);
        return t;
    });

    /**
     * Offloads the status persistence (DB IO) from the event
     * reader thread. Order is preserved per account.
     */
    private final KeyedSerialExecutor statusExecutor = new KeyedSerialExecutor();

    public DefaultRegistrationService(SipClient sipClient, AccountService accountService,
                                      SettingsService settingsService) {

        this.sipClient = sipClient;
        this.accountService = accountService;
        this.settingsService = settingsService;

        sipClient.addRegistrationListener(this);
    }

    @Override
    public void registerAccount(SipAccount account) {

        startRegistrationTimeout(account.getId());

        sipClient.registerAccount(toSipAccountData(account));
    }

    @Override
    public void unregisterAccount(long accountId) {

        sipClient.unregisterAccount(accountId);
    }

    @Override
    public void reprovisionAccount(SipAccount account) {

        if (!registeredAccountIds.contains(account.getId())) {
            return;
        }

        // The teardown is recorded before the uadel goes out so
        // the UNREGISTERED event of this same UA finds it and
        // re-registers with the new data.
        pendingReprovision.put(account.getId(), account);

        sipClient.unregisterAccount(account.getId());
    }

    @Override
    public List<SipAccount> getRegisteredAccounts() {

        return accountService.getAccounts().stream().filter(account -> registeredAccountIds.contains(account.getId()))
                             .toList();
    }

    @Override
    public void addRegistrationListener(RegistrationListener listener) {

        registrationListeners.add(listener);
    }

    @Override
    public void removeRegistrationListener(RegistrationListener listener) {

        registrationListeners.remove(listener);
    }

    @Override
    public void onRegistrationEvent(SipRegistrationEvent event) {

        switch (event.getState()) {

            case REGISTERING -> registeredAccountIds.add(event.getAccountId());

            case REGISTERED -> {
                registeredAccountIds.add(event.getAccountId());
                cancelRegistrationTimeout(event.getAccountId());
            }

            case UNREGISTERED -> {
                cancelRegistrationTimeout(event.getAccountId());
                // A uadel right before a uanew races with the
                // async teardown event of the old UA: that event
                // can land after the uanew and wipe the fresh
                // aor mapping, leaving later registration events
                // unresolved. So a pending reprovision is only
                // re-registered here, once the teardown event
                // itself confirms the old UA is gone.
                SipAccount pending = pendingReprovision.remove(event.getAccountId());

                if (pending != null) {

                    sipClient.registerAccount(toSipAccountData(pending));
                }

                registeredAccountIds.remove(event.getAccountId());
            }

            case FAILED -> {
                cancelRegistrationTimeout(event.getAccountId());
                registeredAccountIds.remove(event.getAccountId());

                // A failed UA stays alive in baresip and keeps
                // re-registering with the stale credentials on
                // every refresh, so the next attempt must start
                // from a clean slate: drop it with a uadel. The
                // resulting UNREGISTERED event has no pending
                // reprovision, so it cannot loop back here. The
                // next registration is manual and arrives seconds
                // later, long after the async teardown event.
                sipClient.unregisterAccount(event.getAccountId());
            }
        }

        AccountStatus status = mapStatus(event.getState());

        statusExecutor.submit(event.getAccountId(), () -> accountService.updateStatus(event.getAccountId(), status));

        registrationListeners.forEach(listener -> listener.onRegistrationEvent(event));
    }

    /**
     * Test hook: blocks until every queued status update has
     * been persisted.
     */
    void awaitStatusFlushed() {

        statusExecutor.awaitIdle();
    }

    /**
     * Starts a per-account timer. If no REGISTERED or FAILED
     * event arrives within the configured timeout, a synthetic
     * FAILED event is emitted and uadel is sent to destroy the
     * stale UA in baresip.
     */
    void startRegistrationTimeout(long accountId) {

        cancelRegistrationTimeout(accountId);

        int seconds = timeoutSeconds();

        ScheduledFuture<?> timer = timerExecutor.schedule(() -> onRegistrationTimeout(accountId), seconds,
                                                          TimeUnit.SECONDS);

        registrationTimers.put(accountId, timer);
    }

    /**
     * Cancels the timer for the given account, if any.
     */
    void cancelRegistrationTimeout(long accountId) {

        ScheduledFuture<?> timer = registrationTimers.remove(accountId);

        if (timer != null) {

            timer.cancel(false);
        }
    }

    @Override
    public void close() {

        registrationTimers.values().forEach(timer -> timer.cancel(false));
        registrationTimers.clear();
        timerExecutor.shutdown();
        statusExecutor.close();
    }

    private void onRegistrationTimeout(long accountId) {

        registrationTimers.remove(accountId);

        String aor = sipClient.getAorForAccount(accountId);

        if (aor == null) {
            aor = "sip:unknown@" + accountId;
        }

        SipRegistrationEvent timeoutEvent = new SipRegistrationEvent(accountId, SipRegistrationState.FAILED, aor, 408,
            "Request Timeout");

        onRegistrationEvent(timeoutEvent);
    }

    private int timeoutSeconds() {

        Optional<String> raw = settingsService.getSetting(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS);

        try {

            int value = Integer.parseInt(raw.orElse(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS_DEFAULT).trim());

            return Math.max(5, Math.min(120, value));

        } catch (NumberFormatException exception) {

            // Fallback with WARN per the exception policy
            // (docs/exceptions.md, rule 1): the persisted value is
            // unparseable but registration must keep working.
            JSoftSipLog.warn("Invalid persisted " + SettingsKeys.REGISTRATION_TIMEOUT_SECONDS + " value '" + raw
                + "', using default 30");

            return 30;
        }
    }

    private AccountStatus mapStatus(SipRegistrationState state) {

        return switch (state) {

            case REGISTERED -> AccountStatus.ONLINE;

            case REGISTERING -> AccountStatus.UNAVAILABLE;

            case FAILED, UNREGISTERED -> AccountStatus.OFFLINE;
        };
    }

    private SipAccountData toSipAccountData(SipAccount account) {

        return new SipAccountData(account.getId(), account.getUsername(), account.getPassword(), account.getDomain(),
            account.getTransport().name());
    }
}