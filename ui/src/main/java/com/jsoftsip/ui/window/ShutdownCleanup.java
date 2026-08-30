package com.jsoftsip.ui.window;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.registration.RegistrationService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Javafx-free shutdown cleanup: hangs up every active call
 * and unregisters every registered account before the app
 * closes. Null services and null entries are tolerated so
 * the launcher can always run it safely.
 *
 * <p>The synchronous {@link #run()} method is kept for tests
 * and callers that already run off the JavaFX thread. The
 * {@link #runAsync()} variant runs the same cleanup on a
 * dedicated daemon thread so the FX thread is never blocked
 * by network IO during shutdown.
 */
public final class ShutdownCleanup {

    private static final ExecutorService SHUTDOWN_EXECUTOR = Executors.newSingleThreadExecutor(daemonThreadFactory());

    private final CallService callService;

    private final RegistrationService registrationService;

    public ShutdownCleanup(CallService callService, RegistrationService registrationService) {

        this.callService = callService;
        this.registrationService = registrationService;
    }

    private static ThreadFactory daemonThreadFactory() {

        return runnable -> {

            Thread thread = new Thread(runnable, "jsoftsip-shutdown");

            thread.setDaemon(true);

            return thread;
        };
    }

    public void hangupActiveCalls() {

        if (callService == null) {
            return;
        }

        for (CallLeg call : callService.getActiveCalls()) {

            if (call != null && call.getBackendCallId() != null) {

                callService.endCall(call.getBackendCallId());
            }
        }
    }

    public void unregisterAllAccounts() {

        if (registrationService == null) {
            return;
        }

        for (SipAccount account : registrationService.getRegisteredAccounts()) {

            if (account != null && account.getId() != null) {

                registrationService.unregisterAccount(account.getId());
            }
        }
    }

    public void run() {

        hangupActiveCalls();
        unregisterAllAccounts();
    }

    /**
     * Runs the cleanup asynchronously on a dedicated daemon
     * thread, returning a future that completes when the work
     * is done. This is the variant used by the launcher during
     * window close so the FX thread stays responsive.
     */
    public CompletableFuture<Void> runAsync() {

        return CompletableFuture.runAsync(this::run, SHUTDOWN_EXECUTOR);
    }

    /**
     * Shuts down the static executor used by {@link #runAsync()}. Must be
     * called once during application termination so the daemon thread does
     * not outlive the JVM.
     */
    public static void close() {

        SHUTDOWN_EXECUTOR.shutdown();
    }
}
