package com.jsoftsip.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for application diagnostics. Owns the SLF4J
 * logger named "jsoftsip" that every module of the app (core, ui
 * and launcher) logs through. The logger is API-only here: core
 * depends on SLF4J but not on any binding, so the appenders are
 * wired programmatically by the native-bridge module via
 * BaresipLogConfig, which attaches the console appender, the
 * rolling file appender and the default INFO level exactly like
 * it does for the "baresip" logger. Every message passes through
 * sanitizeSecrets before logging, so credential values can never
 * reach a log line. The "[JSOFTSIP]" literal is not part of the
 * message: the logback encoder pattern adds it to every line.
 */
public final class JSoftSipLog {

    private static final String LOGGER_NAME = "jsoftsip";

    private static final String SECRET_PATTERN = "(?i)(auth_pass|password|secret)=[^;\\s]*";

    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    private JSoftSipLog() {
    }

    public static void trace(String message) {

        LOGGER.trace("{}", sanitizeSecrets(message));
    }

    public static void debug(String message) {

        LOGGER.debug("{}", sanitizeSecrets(message));
    }

    public static void info(String message) {

        LOGGER.info("{}", sanitizeSecrets(message));
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
     * Canonical secret redaction shared by the whole app. Every
     * occurrence of auth_pass=, password= or secret= followed by
     * a value up to the next semicolon, whitespace or end of the
     * string is replaced with the same key and "***". Pure
     * function, no trim and no side effects. The baresip facade
     * delegates here so there is exactly one implementation.
     */
    public static String sanitizeSecrets(String message) {

        return message.replaceAll(SECRET_PATTERN, "$1=***");
    }
}