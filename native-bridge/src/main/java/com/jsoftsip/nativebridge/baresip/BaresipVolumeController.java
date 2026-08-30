package com.jsoftsip.nativebridge.baresip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Applies the baresip audio volume through the per-app
 * streams that PipeWire/PulseAudio create for the baresip
 * subprocess. Baresip 4.6.0 exposes no volume command over
 * ctrl_tcp, so the volume is applied to the "PipeWire ALSA
 * [baresip]" sink-input and source-output streams via pactl.
 * This affects ONLY the app's own streams, never the system
 * volume.
 */
public class BaresipVolumeController {

    private static final String APPLICATION_NAME = "PipeWire ALSA [baresip]";

    /**
     * Upper bound on a single pactl invocation. A wedged
     * PipeWire must never block a volume worker forever.
     */
    static final long PACTL_TIMEOUT_SECONDS = 2;

    private final Executor executor;

    // Milliseconds so tests can inject a tiny timeout
    private final long timeoutMillis;

    public BaresipVolumeController(Executor executor) {

        this(executor, TimeUnit.SECONDS.toMillis(PACTL_TIMEOUT_SECONDS));
    }

    BaresipVolumeController(Executor executor, long timeoutMillis) {

        this.executor = executor;

        this.timeoutMillis = timeoutMillis;
    }

    public void setOutputVolume(int percent) {

        executor.execute(() -> applyOutputVolume(percent));
    }

    private void applyOutputVolume(int percent) {

        int clampedPercent = clamp(percent);

        List<String> streamIds = findStreamIds("sink-inputs", "Sink Input #");

        for (String streamId : streamIds) {

            runPactl("set-sink-input-volume " + streamId + " " + clampedPercent + "%");
        }

        BaresipLog.debug("Volume applied: " + clampedPercent + "% to " + streamIds.size() + " stream(s)");
    }

    public void setMicrophoneVolume(int percent) {

        executor.execute(() -> applyMicrophoneVolume(percent));
    }

    private void applyMicrophoneVolume(int percent) {

        int clampedPercent = clamp(percent);

        List<String> streamIds = findStreamIds("source-outputs", "Source Output #");

        for (String streamId : streamIds) {

            runPactl("set-source-output-volume " + streamId + " " + clampedPercent + "%");
        }

        BaresipLog.debug("Mic volume applied: " + clampedPercent + "% to " + streamIds.size() + " stream(s)");
    }

    public void setMicrophoneMuted(boolean muted) {

        executor.execute(() -> applyMicrophoneMuted(muted));
    }

    private void applyMicrophoneMuted(boolean muted) {

        List<String> streamIds = findStreamIds("source-outputs", "Source Output #");

        for (String streamId : streamIds) {

            runPactl("set-source-output-mute " + streamId + " " + (muted ? 1 : 0));
        }

        BaresipLog.debug("Mic mute applied: " + muted + " to " + streamIds.size() + " stream(s)");
    }

    List<String> findStreamIds(String listType, String headerPrefix) {

        List<String> ids = new ArrayList<>();

        String output = runPactl("list " + listType);

        if (output == null) {
            return ids;
        }

        String[] blocks = output.split("(?m)^" + headerPrefix);

        for (int i = 1; i < blocks.length; i++) {

            if (blocks[i].contains("application.name = \"" + APPLICATION_NAME + "\"")) {

                String firstLine = blocks[i].split("\\R")[0].trim();

                ids.add(firstLine);
            }
        }

        return ids;
    }

    private int clamp(int percent) {

        if (percent < 0) {
            return 0;
        }

        if (percent > 100) {
            return 100;
        }

        return percent;
    }

    String runPactl(String args) {

        try {

            return runCommand(("pactl " + args).split("\\s+"));

        } catch (IOException exception) {

            BaresipLog.warn("Volume control unavailable: " + exception.getMessage()
                + " (requires PipeWire/PulseAudio pactl)", exception);

            return null;

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            BaresipLog.warn("Volume control unavailable: " + exception.getMessage()
                + " (requires PipeWire/PulseAudio pactl)", exception);

            return null;
        }
    }

    /**
     * Runs the given command and returns its combined output,
     * bounded by the pactl timeout, or null when the exit code
     * is non-zero or the invocation exceeds the timeout.
     * Package private so tests can drive it with a script.
     */
    String runCommand(String... argv) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(argv);

        builder.redirectErrorStream(true);

        Process process = builder.start();

        boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);

        if (!finished) {

            process.destroyForcibly();

            BaresipLog.warn("pactl timed out after " + timeoutMillis + " ms: " + String.join(" ", argv));

            return null;
        }

        // The child already exited, so its stdout pipe is closed
        // and draining here cannot block. Reading before waitFor
        // instead could hang forever if pactl wedged without
        // closing stdout
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

}
