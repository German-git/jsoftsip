package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that BaresipVolumeController public methods return immediately
 * and defer the pactl subprocess work to the supplied executor.
 */
class BaresipVolumeControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void setOutputVolumeReturnsImmediatelyAndRunsAsync() throws InterruptedException {

        BlockingExecutor executor = new BlockingExecutor();
        RecordingController controller = new RecordingController(executor);

        long start = System.currentTimeMillis();
        controller.setOutputVolume(50);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 50, "setOutputVolume must return immediately (was " + elapsed + " ms)");
        assertTrue(controller.commands.isEmpty(), "pactl must not run synchronously on the caller thread");

        executor.runNext();

        assertEquals(1, controller.commands.size(), "the deferred task must execute exactly one pactl command");
        assertTrue(controller.commands.get(0).startsWith("set-sink-input-volume"),
                   "the deferred command must apply the output volume");
    }

    @Test
    void runCommandReturnsFastScriptOutput() throws IOException, InterruptedException {

        // real /bin/sh child exercises the actual process code
        // path that runPactl drives in production
        Path script = executableScript("#!/bin/sh\necho \"Sink Input #57\"\n");

        BaresipVolumeController controller = new BaresipVolumeController(Runnable::run, 250);

        assertEquals("Sink Input #57\n", controller.runCommand(script.toString()));
    }

    @Test
    void nonZeroExitRealProcessYieldsNull() throws IOException, InterruptedException {

        Path script = executableScript("#!/bin/sh\necho boom\nexit 4\n");

        BaresipVolumeController controller = new BaresipVolumeController(Runnable::run, 250);

        assertNull(controller.runCommand(script.toString()));
    }

    @Test
    void wedgedPactlTimesOutReturnsNullAndKillsChild() throws IOException, InterruptedException {

        // exec replaces the shell so destroyForcibly kills the
        // sleeper itself rather than only the wrapper script
        Path pidFile = tempDir.resolve("wedged.pid");

        Path script = executableScript("#!/bin/sh\necho $$ > " + pidFile + "\nexec sleep 30\n");

        BaresipVolumeController controller = new BaresipVolumeController(Runnable::run, 250);

        long startNanos = System.nanoTime();

        String output = controller.runCommand(script.toString());

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertNull(output, "a wedged pactl must degrade exactly like an unavailable one");

        assertTrue(elapsedMillis < 5_000, "must give up well under the 30s sleep, took " + elapsedMillis + " ms");

        assertKilled(pidFile);
    }

    private Path executableScript(String body) throws IOException {

        Path script = tempDir.resolve("fake-pactl.sh");

        Files.writeString(script, body);

        Files.setPosixFilePermissions(script,
                                      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                 PosixFilePermission.OWNER_EXECUTE));

        return script;
    }

    private static void assertKilled(Path pidFile) throws IOException, InterruptedException {

        long pid = Long.parseLong(Files.readString(pidFile).trim());

        long deadline = System.currentTimeMillis() + 2_000;

        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {

            if (System.currentTimeMillis() > deadline) {
                fail("the wedged child must be destroyed after the timeout");
            }

            Thread.sleep(10);
        }
    }

    private static class BlockingExecutor implements Executor {

        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable command) {

            tasks.offer(command);
        }

        public void runNext() throws InterruptedException {

            tasks.take().run();
        }
    }

    private static class RecordingController extends BaresipVolumeController {

        private final List<String> commands = new ArrayList<>();

        RecordingController(Executor executor) {

            super(executor);
        }

        @Override
        List<String> findStreamIds(String listType, String headerPrefix) {

            return List.of("123");
        }

        @Override
        String runPactl(String args) {

            commands.add(args);
            return "";
        }
    }
}
