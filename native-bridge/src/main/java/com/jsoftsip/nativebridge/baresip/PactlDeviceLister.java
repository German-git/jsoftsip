package com.jsoftsip.nativebridge.baresip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lists the PulseAudio/PipeWire devices baresip can use, by
 * parsing the tabular output of "pactl list sinks short" and
 * "pactl list sources short". Each short line carries the
 * device name in its second field.
 *
 * <p>Degradation mirrors BaresipVolumeController: when pactl is
 * missing, cannot be executed, exits non-zero or exceeds the
 * device timeout, the list methods return null and callers fall
 * back to a free text field. Nothing here ever throws for a
 * missing binary and nothing blocks longer than the timeout.
 */
public class PactlDeviceLister {

    /**
     * Upper bound on a single pactl invocation. A wedged
     * PipeWire must never block the caller forever.
     */
    static final long DEVICE_TIMEOUT_SECONDS = 2;

    // Milliseconds so tests can inject a tiny timeout
    private final long timeoutMillis;

    public PactlDeviceLister() {

        this(TimeUnit.SECONDS.toMillis(DEVICE_TIMEOUT_SECONDS));
    }

    PactlDeviceLister(long timeoutMillis) {

        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Names of the available output devices, or null when pactl
     * is unavailable.
     */
    public List<String> listSinks() {

        return listDevices("sinks");
    }

    /**
     * Names of the available input devices, or null when pactl
     * is unavailable.
     */
    public List<String> listSources() {

        return listDevices("sources");
    }

    private List<String> listDevices(String kind) {

        final String output;

        try {

            output = run("pactl", "list", kind, "short");

        } catch (IOException exception) {

            logUnavailable(exception);

            return null;

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            logUnavailable(exception);

            return null;
        }

        if (output == null) {
            return null;
        }

        return parseShortListing(output);
    }

    /**
     * Parses the short listing format into device names. Lines
     * with fewer than two fields are skipped so a malformed
     * line never breaks the whole listing.
     */
    static List<String> parseShortListing(String output) {

        List<String> names = new ArrayList<>();

        for (String line : output.split("\\R")) {

            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            String[] fields = trimmed.split("\\s+");

            if (fields.length < 2) {
                continue;
            }

            names.add(fields[1]);
        }

        return names;
    }

    /**
     * Runs pactl with the given fixed arguments and returns its
     * combined output, or null when the exit code is non-zero or
     * the invocation exceeds the timeout. Package private so
     * tests can substitute the process call.
     */
    String run(String... args) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(args);

        builder.redirectErrorStream(true);

        Process process = builder.start();

        boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);

        if (!finished) {

            process.destroyForcibly();

            BaresipLog.warn("Device listing timed out after " + timeoutMillis + " ms: " + String.join(" ", args));

            return null;
        }

        // The child already exited, so its stdout pipe is closed
        // and draining here cannot block. Reading before waitFor
        // instead could hang forever if pactl wedged without
        // closing stdout, and short listings stay far below the
        // pipe buffer limit so drain-after-exit loses nothing
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;

            while ((line = reader.readLine()) != null) {

                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return null;
        }

        return output.toString();
    }

    private static void logUnavailable(Throwable exception) {

        BaresipLog.warn("Device listing unavailable: " + exception.getMessage()
            + " (requires PipeWire/PulseAudio pactl)", exception);
    }
}
