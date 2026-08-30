package com.jsoftsip.nativebridge.baresip;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic recovery for the Baresip backend. Registered as the
 * unexpected-exit listener of the process manager: when the
 * subprocess dies without an intentional stop (crash, SIGKILL,
 * ctrl_tcp host death), a single bounded recovery cycle runs the
 * shared session-restart operation until it succeeds or the
 * attempt budget is exhausted.
 *
 * <p>Concurrency model: notifications arrive off the exit thread
 * and are coalesced with an atomic guard, so concurrent deaths
 * never stack parallel recovery cycles, at most one cycle runs at
 * any time on a dedicated single-thread executor. Intentional
 * stops (settings apply, app shutdown) are already suppressed by
 * the intent flag in {@link BaresipProcessManager}, and this
 * supervisor additionally goes inert after its own shutdown so
 * application teardown can never trigger recovery work.
 *
 * <p>After exhausting the attempts the supervisor logs that the
 * backend stays down and manual action (applying settings) is
 * required, then stays inert: a later death event may start a
 * fresh cycle with a fresh attempt budget.
 */
public class BaresipSupervisor {

    private static final int MAX_ATTEMPTS = 3;

    private static final long DEFAULT_BACKOFF_BASE_MS = 1000;

    private final BaresipProcessManager processManager;

    private final BaresipConfigService.RestartOperation restartOperation;

    private final long backoffBaseMillis;

    // Coalescing guard: exactly one recovery cycle may exist per
    // burst of death notifications
    private final AtomicBoolean recovering = new AtomicBoolean(false);

    private final ExecutorService recoveryExecutor;

    private volatile boolean shutDown;

    public BaresipSupervisor(BaresipProcessManager processManager,
                             BaresipConfigService.RestartOperation restartOperation) {

        this(processManager, restartOperation, DEFAULT_BACKOFF_BASE_MS);
    }

    /**
     * Test seam: the backoff base is injectable so suites run
     * with negligible delays between attempts.
     */
    BaresipSupervisor(BaresipProcessManager processManager, BaresipConfigService.RestartOperation restartOperation,
                      long backoffBaseMillis) {

        this.processManager = processManager;

        this.restartOperation = restartOperation;

        this.backoffBaseMillis = backoffBaseMillis;

        // Single-threaded by construction and virtual-backed so
        // an idle pool never pins the JVM at exit
        this.recoveryExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("baresip-supervisor-", 0)
                                                                        .factory());
    }

    /**
     * Starts supervision: registers this supervisor as the
     * unexpected-exit handler of the process manager and marks
     * it active again, including after a previous shutdown.
     */
    public void arm() {

        shutDown = false;

        processManager.setUnexpectedExitListener(this::onUnexpectedExit);
    }

    /**
     * Stops supervision idempotently: unregisters from the
     * manager, refuses further notifications and interrupts any
     * in-flight attempt delay. Must be called first in the
     * application teardown chain, before the process itself is
     * stopped, so teardown never looks like a crash to recover.
     */
    public void shutdown() {

        shutDown = true;

        processManager.setUnexpectedExitListener(null);

        recoveryExecutor.shutdownNow();
    }

    private void onUnexpectedExit() {

        if (shutDown) {
            return;
        }

        // Coalesce: while a cycle runs or is queued every further
        // notification is absorbed by that cycle instead of
        // stacking recoveries
        if (!recovering.compareAndSet(false, true)) {
            return;
        }

        try {

            recoveryExecutor.submit(this::runRecoveryCycle);

        } catch (RejectedExecutionException exception) {

            // Shutdown raced the submission: leave nothing pending
            recovering.set(false);
        }
    }

    private void runRecoveryCycle() {

        try {

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

                // Skip-and-reset conditions are re-checked before
                // every attempt: supervision may have been shut
                // down or someone else may have recovered the
                // process meanwhile
                if (!shouldAttempt()) {
                    return;
                }

                if (attempt > 1 && !sleepBeforeRetry(attempt)) {
                    return;
                }

                if (!shouldAttempt()) {
                    return;
                }

                if (restartSafely()) {
                    return;
                }
            }

            if (!shutDown) {
                BaresipLog.error("Baresip could not be recovered automatically, the backend stays down until a "
                    + "manual settings apply");
            }

        } finally {

            recovering.set(false);
        }
    }

    private boolean shouldAttempt() {

        if (shutDown || processManager.isRunning()) {

            // Someone else brought the backend back or teardown
            // began: no recovery work belongs to us anymore
            return false;
        }

        return true;
    }

    /**
     * Exponential backoff between attempts: base delay after the
     * first failure, doubling on every further retry. Returns
     * false when interrupted, which ends the cycle quietly since
     * only shutdown interrupts it.
     */
    private boolean sleepBeforeRetry(int attempt) {

        long delayMillis = backoffBaseMillis << (attempt - 2);

        try {

            Thread.sleep(delayMillis);

            return true;

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            return false;
        }
    }

    private boolean restartSafely() {

        try {

            boolean recovered = restartOperation.restart();

            if (recovered) {
                BaresipLog.info("Baresip recovered automatically after an unexpected exit");
            }

            return recovered;

        } catch (RuntimeException exception) {

            BaresipLog.warn("Baresip recovery attempt failed", exception);

            return false;
        }
    }
}
