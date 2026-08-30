package com.jsoftsip.nativebridge.baresip;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Programmatic Logback wiring for the "baresip" and "jsoftsip"
 * loggers. The console appenders on System.out are always active,
 * the rolling file appenders behind async appenders are attached
 * or detached at runtime so the Save-to-log-file preference
 * applies without restarting the app. Both loggers share the same
 * policy (2 MB per file, 5 files kept) but keep their own files
 * and line prefixes, so the "baresip" format stays untouched.
 */
public final class BaresipLogConfig {

    private static final String CONSOLE_APPENDER_NAME = "BARESIP_CONSOLE";

    private static final String JSOFTSIP_CONSOLE_APPENDER_NAME = "JSOFTSIP_CONSOLE";

    private static final String ASYNC_APPENDER_NAME = "BARESIP_ASYNC";

    private static final String JSOFTSIP_ASYNC_APPENDER_NAME = "JSOFTSIP_ASYNC";

    private static final String FILE_APPENDER_NAME = "BARESIP_FILE";

    private static final String JSOFTSIP_FILE_APPENDER_NAME = "JSOFTSIP_FILE";

    private static final String ENCODER_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [BARESIP] %msg%n";

    private static final String JSOFTSIP_ENCODER_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [JSOFTSIP] %msg%n";

    private static final String FILE_NAME = "baresip.log";

    private static final String JSOFTSIP_FILE_NAME = "jsoftsip.log";

    private static final FileSize MAX_FILE_SIZE = FileSize.valueOf("2MB");

    private static final int MAX_FILE_HISTORY = 5;

    private static final int ASYNC_QUEUE_CAPACITY = 10000;

    private BaresipLogConfig() {
    }

    /**
     * Idempotent: wires the console appenders and the default
     * INFO levels for both the "baresip" and "jsoftsip" loggers.
     * Called from the BaresipLog static block and from the
     * launcher entry point, so no message is ever written before
     * the appenders exist.
     */
    public static synchronized void ensureConfigured() {

        ensureConsoleAppender();
        ensureJSoftSipConsoleAppender();
        ensureDefaultLevel();
    }

    /**
     * Idempotent: attaches the System.out console appender to the
     * "baresip" logger exactly once.
     */
    public static synchronized void ensureConsoleAppender() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger logger = context.getLogger("baresip");

        if (logger.getAppender(CONSOLE_APPENDER_NAME) != null) {

            return;
        }

        ConsoleAppender<ILoggingEvent> console = newConsoleAppender(context, CONSOLE_APPENDER_NAME, ENCODER_PATTERN);

        logger.addAppender(console);
    }

    /**
     * Idempotent: attaches the System.out console appender to the
     * "jsoftsip" logger exactly once, with the same timestamp
     * pattern as baresip but the "[JSOFTSIP]" prefix.
     */
    public static synchronized void ensureJSoftSipConsoleAppender() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger logger = context.getLogger("jsoftsip");

        if (logger.getAppender(JSOFTSIP_CONSOLE_APPENDER_NAME) != null) {

            return;
        }

        ConsoleAppender<ILoggingEvent> console = newConsoleAppender(context, JSOFTSIP_CONSOLE_APPENDER_NAME,
                                                                    JSOFTSIP_ENCODER_PATTERN);

        logger.addAppender(console);
    }

    /**
     * Idempotent: pins the "baresip" and "jsoftsip" loggers to
     * INFO. With the debug() surfaces in place this keeps
     * diagnostics silent unless a higher verbosity is explicitly
     * requested, and changes nothing for the existing info-level
     * messages.
     */
    public static synchronized void ensureDefaultLevel() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        context.getLogger("baresip").setLevel(Level.INFO);

        context.getLogger("jsoftsip").setLevel(Level.INFO);
    }

    /**
     * Attaches a rolling file appender (2 MB per file, 5 files
     * kept, named baresip.log) wrapped in an async appender with
     * a 10000-event queue and neverBlock, so baresip output
     * never stalls the reader. The given directory and its
     * parents are created on demand.
     */
    public static synchronized void attachFileAppender(Path logDirectory) throws IOException {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger logger = context.getLogger("baresip");

        attachFileAppender(context, logger, ASYNC_APPENDER_NAME, FILE_APPENDER_NAME, FILE_NAME, ENCODER_PATTERN,
                           logDirectory);
    }

    /**
     * Attaches the "jsoftsip" rolling file appender with the same
     * rotation policy and async wrapper as baresip but its own
     * file (jsoftsip.log) and "[JSOFTSIP]" line prefix.
     */
    public static synchronized void attachJSoftSipFileAppender(Path logDirectory) throws IOException {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger logger = context.getLogger("jsoftsip");

        attachFileAppender(context, logger, JSOFTSIP_ASYNC_APPENDER_NAME, JSOFTSIP_FILE_APPENDER_NAME,
                           JSOFTSIP_FILE_NAME, JSOFTSIP_ENCODER_PATTERN, logDirectory);
    }

    /**
     * Detaches and stops the baresip async file appender,
     * flushing any queued events. No-op when the appender is not
     * attached.
     */
    public static synchronized void detachFileAppender() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        detachFileAppender(context, context.getLogger("baresip"), ASYNC_APPENDER_NAME);
    }

    /**
     * Detaches and stops the jsoftsip async file appender,
     * flushing any queued events. No-op when the appender is not
     * attached.
     */
    public static synchronized void detachJSoftSipFileAppender() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        detachFileAppender(context, context.getLogger("jsoftsip"), JSOFTSIP_ASYNC_APPENDER_NAME);
    }

    public static synchronized boolean isFileAppenderAttached() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        return context.getLogger("baresip").getAppender(ASYNC_APPENDER_NAME) != null;
    }

    public static synchronized boolean isJSoftSipFileAppenderAttached() {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        return context.getLogger("jsoftsip").getAppender(JSOFTSIP_ASYNC_APPENDER_NAME) != null;
    }

    private static ConsoleAppender<ILoggingEvent> newConsoleAppender(LoggerContext context, String appenderName,
                                                                     String pattern) {

        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();

        console.setName(appenderName);
        console.setContext(context);
        console.setTarget("System.out");
        console.setEncoder(newEncoder(context, pattern));
        console.start();

        return console;
    }

    private static void attachFileAppender(LoggerContext context, Logger logger, String asyncAppenderName,
                                           String fileAppenderName, String fileName, String pattern, Path logDirectory)
        throws IOException {

        if (logger.getAppender(asyncAppenderName) != null) {

            return;
        }

        Files.createDirectories(logDirectory);

        RollingFileAppender<ILoggingEvent> rolling = new RollingFileAppender<>();

        rolling.setName(fileAppenderName);
        rolling.setContext(context);
        rolling.setFile(logDirectory.resolve(fileName).toString());
        rolling.setAppend(true);
        rolling.setEncoder(newEncoder(context, pattern));

        FixedWindowRollingPolicy window = new FixedWindowRollingPolicy();

        window.setContext(context);
        window.setParent(rolling);
        window.setFileNamePattern(logDirectory.resolve(fileName + ".%i").toString());
        window.setMinIndex(1);
        window.setMaxIndex(MAX_FILE_HISTORY);

        SizeBasedTriggeringPolicy<ILoggingEvent> trigger = new SizeBasedTriggeringPolicy<>();

        trigger.setContext(context);
        trigger.setMaxFileSize(MAX_FILE_SIZE);

        rolling.setRollingPolicy(window);
        rolling.setTriggeringPolicy(trigger);

        window.start();
        trigger.start();
        rolling.start();

        AsyncAppender async = new AsyncAppender();

        async.setName(asyncAppenderName);
        async.setContext(context);
        async.setQueueSize(ASYNC_QUEUE_CAPACITY);
        async.setNeverBlock(true);
        async.addAppender(rolling);
        async.start();

        logger.addAppender(async);
    }

    private static void detachFileAppender(LoggerContext context, Logger logger, String asyncAppenderName) {

        Appender<ILoggingEvent> async = logger.getAppender(asyncAppenderName);

        if (async == null) {
            return;
        }

        logger.detachAppender(async);

        async.stop();
    }

    private static PatternLayoutEncoder newEncoder(LoggerContext context, String pattern) {

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();

        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();

        return encoder;
    }
}