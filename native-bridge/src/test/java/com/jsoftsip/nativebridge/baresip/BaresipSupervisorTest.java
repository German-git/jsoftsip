package com.jsoftsip.nativebridge.baresip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaresipSupervisorTest {

    private static final long TEST_BACKOFF_MS = 1;

    private final FakeProcessManager processManager = new FakeProcessManager();

    @Test
    void deathEventTriggersRestartOperation() throws Exception {

        AtomicInteger restarts = new AtomicInteger();

        BaresipSupervisor supervisor = new BaresipSupervisor(processManager, () -> {
            restarts.incrementAndGet();
            return true;
        }, TEST_BACKOFF_MS);

        supervisor.arm();

        simulateUnexpectedDeath();

        await(() -> restarts.get() == 1, "an unexpected death must trigger the recovery operation");
    }

    @Test
    void failingTwiceThenSucceedingRunsExactlyThreeRestarts() throws Exception {

        AtomicInteger restarts = new AtomicInteger();

        Deque<Boolean> outcomes = new ArrayDeque<>(List.of(false, false, true));

        BaresipSupervisor supervisor = new BaresipSupervisor(processManager, () -> {
            restarts.incrementAndGet();
            return outcomes.isEmpty() || outcomes.pop();
        }, TEST_BACKOFF_MS);

        supervisor.arm();

        simulateUnexpectedDeath();

        await(() -> restarts.get() == 3, "the cycle must retry until a restart succeeds");

        Thread.sleep(100);

        assertEquals(3, restarts.get(), "a successful restart must end the cycle immediately");
    }

    @Test
    void alwaysFailingStopsAtMaxAttemptsAndLogsTerminalError() throws Exception {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            AtomicInteger restarts = new AtomicInteger();

            BaresipSupervisor supervisor = new BaresipSupervisor(processManager, () -> {
                restarts.incrementAndGet();
                return false;
            }, TEST_BACKOFF_MS);

            supervisor.arm();

            simulateUnexpectedDeath();

            await(() -> restarts.get() == 3, "the cycle must run exactly three bounded attempts");

            await(() -> hasErrorAboutManualAction(appender), "exhausted attempts must log the manual-action error");

            Thread.sleep(100);

            assertEquals(3, restarts.get(), "no attempt may run beyond the bound");

            assertEquals(1,
                         appender.list.stream()
                                      .filter(event -> event.getLevel() == Level.ERROR
                                          && event.getFormattedMessage().contains("manual"))
                                      .count(),
                         "the terminal state must be reported exactly once per cycle");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shutdownMakesFurtherDeathEventsInert() throws Exception {

        AtomicInteger restarts = new AtomicInteger();

        BaresipSupervisor supervisor = new BaresipSupervisor(processManager, () -> {
            restarts.incrementAndGet();
            return true;
        }, TEST_BACKOFF_MS);

        supervisor.arm();

        supervisor.shutdown();

        assertNull(processManager.listener, "shutdown must unregister the listener from the manager");

        // Even a listener reference kept alive by anyone else must
        // be ignored after shutdown: teardown never triggers recovery
        Runnable leaked = processManager.lastRegisteredListener;

        if (leaked == null) {
            fail("arm must have registered a listener before shutdown");
        }

        processManager.running = false;

        leaked.run();

        Thread.sleep(150);

        assertEquals(0, restarts.get(), "death events after shutdown must not trigger any recovery");
    }

    @Test
    void concurrentNotificationsAreCoalescedIntoOneCycle() throws Exception {

        CountDownLatch insideFirstAttempt = new CountDownLatch(1);

        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);

        AtomicInteger restarts = new AtomicInteger();

        BaresipConfigService.RestartOperation slowRestart = () -> {

            int call = restarts.incrementAndGet();

            if (call == 1) {
                try {
                    insideFirstAttempt.countDown();
                    releaseFirstAttempt.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return false;

            } else if (call == 2) {
                return true;
            }

            return false;
        };

        BaresipSupervisor supervisor = new BaresipSupervisor(processManager, slowRestart, TEST_BACKOFF_MS);

        supervisor.arm();

        simulateUnexpectedDeath();

        assertTrue(insideFirstAttempt.await(2, TimeUnit.SECONDS), "the first attempt must start");

        // A burst of concurrent deaths while the cycle is running:
        // every one of them must be absorbed by the running cycle
        for (int i = 0; i < 5; i++) {
            simulateUnexpectedDeath();
        }

        releaseFirstAttempt.countDown();

        await(() -> restarts.get() >= 2, "the coalesced cycle must resume after the block");

        // Settle window: stacked cycles would keep firing attempts
        // well past this point, since every extra cycle retries
        Thread.sleep(400);

        assertEquals(2, restarts.get(), "concurrent notifications must never stack parallel or extra recovery cycles");
    }

    @Test
    void processAliveAgainSkipsRemainingAttempts() throws Exception {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            AtomicInteger restarts = new AtomicInteger();

            BaresipSupervisor supervisor = new BaresipSupervisor(processManager, () -> {

                restarts.incrementAndGet();

                // Someone else recovered the backend between our
                // failed first attempt and the next one
                processManager.running = restarts.get() >= 1;

                return false;
            }, TEST_BACKOFF_MS);

            supervisor.arm();

            simulateUnexpectedDeath();

            await(() -> restarts.get() == 1, "the first attempt must run against the dead backend");

            Thread.sleep(200);

            assertEquals(1, restarts.get(), "remaining attempts must be skipped when the process is alive again");

            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR),
                       "a backend recovered by someone else must not log the terminal error");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void simulateUnexpectedDeath() throws InterruptedException {

        Runnable listener = processManager.listener;

        if (listener == null) {
            fail("the supervisor must be registered as the unexpected-exit listener");
        }

        processManager.running = false;

        listener.run();
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {

        long deadline = System.currentTimeMillis() + 2_000;

        while (!condition.getAsBoolean()) {

            if (System.currentTimeMillis() > deadline) {
                fail(message);
            }

            Thread.sleep(5);
        }
    }

    private static boolean hasErrorAboutManualAction(ListAppender<ILoggingEvent> appender) {

        return appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
            && event.getFormattedMessage().contains("manual"));
    }

    /**
     * Process manager fake: captures the unexpected-exit listener
     * registrations and fakes the alive state without spawning any
     * real baresip process.
     */
    private static final class FakeProcessManager extends BaresipProcessManager {

        private Runnable listener;

        private Runnable lastRegisteredListener;

        private volatile boolean running;

        @Override
        public void setUnexpectedExitListener(Runnable listener) {

            // Remember only genuine registrations: an unregister
            // (null) clears the active listener but must keep the
            // last armed one available for leak assertions
            if (listener != null) {
                this.lastRegisteredListener = listener;
            }

            this.listener = listener;
        }

        @Override
        public boolean isRunning() {

            return running;
        }
    }
}
