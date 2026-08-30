package com.jsoftsip.core.util;

/**
 * JavaFX-free formatting of call durations as "m:ss".
 * Kept free of JavaFX imports so plain unit tests reach it headless.
 * Shared between the core domain and the UI to avoid two competing
 * formats for the same concept.
 */
public final class CallDurationFormatter {

    private CallDurationFormatter() {
    }

    public static String format(long seconds) {

        long minutes = seconds / 60;

        long remainingSeconds = seconds % 60;

        return minutes + ":" + (remainingSeconds < 10 ? "0" : "") + remainingSeconds;
    }
}
