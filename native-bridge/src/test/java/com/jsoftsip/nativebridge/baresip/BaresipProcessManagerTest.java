package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaresipProcessManagerTest {

    @TempDir
    Path tempDir;

    private BaresipProcessManager manager;

    @AfterEach
    void tearDown() {

        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void stopWaitsForAProcessThatAcceptsSigterm() throws Exception {

        manager = new BaresipProcessManager();

        manager.start(tempDir,
                      executableScript("#!/bin/sh\ntrap 'exit 0' TERM\nwhile true; do sleep 1; done\n").toString());

        long startedAt = System.nanoTime();

        manager.stop();

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertFalse(manager.isRunning(), "a cooperating process must be stopped");

        assertTrue(elapsedMs < 4000, "a cooperating process must not wait for the grace window");
    }

    @Test
    void stopForcesTerminationWhenTheProcessIgnoresSigterm() throws Exception {

        manager = new BaresipProcessManager();

        manager.start(tempDir, executableScript("#!/bin/sh\ntrap '' TERM\nwhile true; do sleep 1; done\n").toString());

        manager.stop();

        assertFalse(manager.isRunning(), "the escalation must kill a SIGTERM-ignoring process");
    }

    @Test
    void unexpectedExitFiresTheListenerExactlyOnce() throws Exception {

        manager = new BaresipProcessManager();

        AtomicInteger deaths = new AtomicInteger();

        manager.setUnexpectedExitListener(deaths::incrementAndGet);

        manager.start(tempDir, executableScript("#!/bin/sh\nwhile true; do sleep 1; done\n").toString());

        manager.getProcess().destroy();

        await(() -> deaths.get() >= 1, "an unexpected death must reach the registered listener");

        // Give any duplicate notification time to surface
        // before pinning the exactly-once guarantee
        Thread.sleep(200);

        assertEquals(1, deaths.get(), "the listener must fire exactly once per unexpected death");
    }

    @Test
    void deliberateStopDoesNotFireTheListener() throws Exception {

        manager = new BaresipProcessManager();

        AtomicInteger deaths = new AtomicInteger();

        manager.setUnexpectedExitListener(deaths::incrementAndGet);

        manager.start(tempDir,
                      executableScript("#!/bin/sh\ntrap 'exit 0' TERM\nwhile true; do sleep 1; done\n").toString());

        manager.stop();

        Thread.sleep(200);

        assertEquals(0, deaths.get(), "an intentional stop must not notify the listener");
    }

    @Test
    void oldProcessDeathAfterReplacementDoesNotFireTheListener() throws Exception {

        manager = new BaresipProcessManager();

        AtomicInteger deaths = new AtomicInteger();

        manager.setUnexpectedExitListener(deaths::incrementAndGet);

        manager.start(tempDir,
                      executableScript("#!/bin/sh\ntrap 'exit 0' TERM\nwhile true; do sleep 1; done\n").toString());

        Process oldProcess = manager.getProcess();

        manager.stop();

        manager.start(tempDir, executableScript("#!/bin/sh\nwhile true; do sleep 1; done\n").toString());

        // Make sure the replaced generation is fully gone: its
        // late exit callback belongs to a process the manager no
        // longer owns and must never reach the listener
        oldProcess.destroyForcibly();

        Thread.sleep(300);

        assertEquals(0, deaths.get(), "a replaced process generation must never trigger the listener");
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

    private Path executableScript(String body) throws IOException {

        Path script = tempDir.resolve("fake-baresip.sh");

        Files.writeString(script, body);

        Files.setPosixFilePermissions(script,
                                      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                 PosixFilePermission.OWNER_EXECUTE));

        return script;
    }
}