package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.logging.JSoftSipLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for baresip diagnostics. Owns the SLF4J
 * logger named "baresip" and guarantees the console appenders and
 * the default INFO levels for both the "baresip" and "jsoftsip"
 * loggers are active before the first message is logged. Exposes
 * debug, warn and error levels, the latter two with throwable
 * support. Every message passes through sanitizeSecrets before
 * logging, so credential values can never reach a log line. The
 * "[BARESIP]" literal is not part of the message: the logback
 * encoder pattern adds it to every line.
 */
public final class BaresipLog {

    private static final String LOGGER_NAME = "baresip";

    private static final String ANSI_ESCAPE_PATTERN = "\u001B\\[[0-9;]*[A-Za-z]";

    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    static {
        BaresipLogConfig.ensureConfigured();
    }

    private BaresipLog() {
    }

    public static void debug(String message) {

        LOGGER.debug("{}", sanitizeSecrets(message));
    }

    public static void info(String message) {

        LOGGER.info("{}", sanitizeSecrets(message));
    }

    public static void debug(String message, Throwable throwable) {

        LOGGER.debug("{}", sanitizeSecrets(message), throwable);
    }

    public static void warn(String message) {

        LOGGER.warn("{}", sanitizeSecrets(message));
    }

    public static void error(String message) {

        LOGGER.error("{}", sanitizeSecrets(message));
    }

    public static void warn(String message, Throwable throwable) {

        LOGGER.warn("{}", sanitizeSecrets(message), throwable);
    }

    public static void error(String message, Throwable throwable) {

        LOGGER.error("{}", sanitizeSecrets(message), throwable);
    }

    /**
     * Redacts credential values in a diagnostic message. Every
     * occurrence of auth_pass=, password= or secret= followed by
     * a value up to the next semicolon, whitespace or end of the
     * string is replaced with the same key and "***". Pure
     * function, no trim and no side effects. Delegates to the
     * canonical implementation in the core facade so the app has
     * exactly one secret-redaction rule.
     */
    public static String sanitizeSecrets(String message) {

        return JSoftSipLog.sanitizeSecrets(message);
    }

    /**
     * Strips ANSI SGR escape sequences and trims padding so a
     * raw baresip line is safe to log or persist.
     */
    public static String sanitize(String line) {

        return line.replaceAll(ANSI_ESCAPE_PATTERN, "").trim();
    }
}