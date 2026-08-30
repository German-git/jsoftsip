package com.jsoftsip.ui;

import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Starts and stops the JavaFX toolkit for UI tests. Classes that
 * need the toolkit call acquire() in @BeforeAll and release() in
 * @AfterAll. The toolkit is started once per JVM and only exited
 * after a short delay once the last acquiring class has released,
 * so sequential test classes never try to restart a toolkit that
 * Platform.exit() has already terminated. The delay keeps the FX
 * thread alive long enough for the next class to acquire without
 * keeping the JVM alive after the test suite finishes.
 */
public final class FxTestToolkit {

    private static final AtomicInteger ACTIVE = new AtomicInteger();

    private static final ScheduledExecutorService EXIT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());

    private static volatile ScheduledFuture<?> pendingExit;

    private FxTestToolkit() {
    }

    public static void acquire() {

        ScheduledFuture<?> pending = pendingExit;

        if (pending != null && !pending.isDone()) {

            pending.cancel(false);
        }

        if (ACTIVE.incrementAndGet() == 1) {

            try {

                Platform.startup(() -> {
                });

            } catch (IllegalStateException alreadyStarted) {

                // Toolkit already running, started elsewhere
            }
        }
    }

    public static void release() {

        if (ACTIVE.decrementAndGet() == 0) {

            pendingExit = EXIT_SCHEDULER.schedule(() -> {

                if (ACTIVE.get() == 0) {

                    Platform.exit();
                }

            }, 2, TimeUnit.SECONDS);
        }
    }

    private static ThreadFactory daemonThreadFactory() {

        return runnable -> {

            Thread thread = new Thread(runnable, "jsoftsip-fx-toolkit-exit");

            thread.setDaemon(true);

            return thread;
        };
    }
}
