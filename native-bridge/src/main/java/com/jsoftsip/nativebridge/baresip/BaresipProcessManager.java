package com.jsoftsip.nativebridge.baresip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BaresipProcessManager {

    private static final long STOP_GRACE_MS = 5000;

    private Process process;

    private BaresipOutputReader outputReader;

    // True while a deliberate stop is in flight: the exit of the
    // process being destroyed is the expected outcome, never a
    // crash worth reporting
    private boolean intentionalShutdown;

    private Runnable unexpectedExitListener;

    /**
     * Matches the periodic audio statistics baresip emits during
     * calls, e.g. "[0:00:10] audio=64000/0 ...". These repeat
     * constantly and are filtered out so the app log only carries
     * real baresip output.
     */
    private static final Pattern AUDIO_STATS_PATTERN = Pattern.compile("audio=\\d+/\\d+");

    /**
     * Registers the single listener notified when the current
     * process dies unexpectedly, i.e. without a preceding stop.
     * A null listener restores the previous silent behavior. The
     * listener is invoked outside the manager monitor and off the
     * caller thread, so it may safely trigger recovery work such
     * as a process restart.
     */
    public synchronized void setUnexpectedExitListener(Runnable listener) {

        this.unexpectedExitListener = listener;
    }

    public synchronized void start(Path workingDirectory, String executable) throws IOException {

        if (isRunning()) {
            return;
        }

        // -f forces Baresip to use the given directory as its config
        // dir, without it Baresip falls back to ~/.baresip, which is
        // the root cause of it picking up unrelated accounts.
        ProcessBuilder builder = new ProcessBuilder(executable, "-f", workingDirectory.toString());

        builder.directory(workingDirectory.toFile());

        builder.redirectErrorStream(true);

        process = builder.start();

        // A fresh generation owns supervision again: clear the
        // intent flag left by the previous stop so only real
        // crashes reach the exit watch below
        intentionalShutdown = false;

        Process captured = process;

        captured.onExit().thenRun(() -> handleExit(captured));

        startOutputReader();
    }

    /**
     * Decides whether an exit belongs to supervision or to an
     * expected teardown. The instance lock only snapshots the
     * decision and the listener reference: the listener itself
     * runs outside the lock so recovery work never executes
     * while holding the process monitor. A death of a replaced
     * generation (process field no longer pointing at the exited
     * instance) is stale news from an older stop or restart and
     * is suppressed by identity.
     */
    private void handleExit(Process captured) {

        Runnable listener;

        synchronized (this) {

            if (intentionalShutdown || process != captured) {
                return;
            }

            listener = unexpectedExitListener;
        }

        if (listener == null) {
            return;
        }

        listener.run();
    }

    /**
     * Drains the Baresip output stream and forwards every line to
     * the app console. Baresip writes status lines continuously
     * during calls, if the pipe is never read it fills up (64 KB)
     * and Baresip blocks on write, freezing the audio pipeline.
     * The reader also makes Baresip's raw logs visible for
     * diagnostics.
     */
    private void startOutputReader() {

        outputReader = new BaresipOutputReader(
            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));

        outputReader.addListener(this::printLine);

        outputReader.start();
    }

    private void printLine(String line) {

        // Baresip colours some output (ANSI SGR) and pads status
        // lines with trailing whitespace for terminal overwrite.
        String sanitized = BaresipLog.sanitize(line);

        if (!sanitized.isEmpty() && !AUDIO_STATS_PATTERN.matcher(sanitized).find()) {

            BaresipLog.info(sanitized);
        }
    }

    public synchronized void stop() {

        if (process == null) {
            return;
        }

        // Flag before destroy: the exit callback can fire the
        // moment destroy lands, and an intentional close must
        // already be recognizable as such by then
        intentionalShutdown = true;

        // Stop the reader first: marking it stopped makes the
        // stream-close IOException below a silent, expected
        // shutdown path instead of a reported error.
        if (outputReader != null) {

            outputReader.stop();

            outputReader = null;
        }

        process.destroy();

        // SIGTERM first, then escalate to SIGKILL: a baresip
        // stuck in a native call must not hang the shutdown
        // forever on a non-responsive process
        if (!awaitExit(process)) {

            BaresipLog.warn("Baresip ignored SIGTERM, forcing termination");

            process.destroyForcibly();

            // A process that survives SIGKILL
            // (uninterruptible native call, stuck zombie) used to
            // vanish silently while still holding audio devices
            // and ports, surface it instead of ignoring the wait
            if (!awaitExit(process)) {

                long liveChildren = process.descendants().count();

                BaresipLog.error("Baresip is still alive after SIGKILL (pid " + process.pid() + ", " + liveChildren
                    + " live child processes); it may keep holding audio devices and ports");
            }
        }

        process = null;
    }

    private static boolean awaitExit(Process target) {

        try {

            return target.waitFor(STOP_GRACE_MS, TimeUnit.MILLISECONDS);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            return false;
        }
    }

    public synchronized boolean isRunning() {

        return process != null && process.isAlive();
    }

    public synchronized Process getProcess() {

        if (process == null) {

            throw new IllegalStateException("Baresip process not started.");
        }

        return process;
    }
}