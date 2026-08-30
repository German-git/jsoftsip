package com.jsoftsip.nativebridge.baresip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jsoftsip.core.logging.JSoftSipLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaresipLogConfigTest {

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
        + "\\.\\d{3}[+-]\\d{2}:\\d{2}" + " \\[BARESIP\\] .+$");

    private static final Pattern JSOFTSIP_LOG_LINE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
        + "\\.\\d{3}[+-]\\d{2}:\\d{2}" + " \\[JSOFTSIP\\] .+$");

    @TempDir
    Path logDir;

    @AfterEach
    void tearDown() {

        BaresipLogConfig.detachFileAppender();
        BaresipLogConfig.detachJSoftSipFileAppender();
    }

    @Test
    void attachCreatesLogFileInGivenDirWithTimestampPattern() throws Exception {

        BaresipLogConfig.attachFileAppender(logDir);

        BaresipLog.info("hello log");

        BaresipLogConfig.detachFileAppender();

        String line = awaitLogLine(logDir.resolve("baresip.log"), "hello log");

        assertTrue(LOG_LINE_PATTERN.matcher(line).matches(), "line must carry local time with ms and offset: " + line);
    }

    @Test
    void attachCreatesDirectoriesOnDemand() throws Exception {

        Path deepLogDir = logDir.resolve("a").resolve("b").resolve("logs");

        BaresipLogConfig.attachFileAppender(deepLogDir);

        BaresipLog.info("deep dir");

        BaresipLogConfig.detachFileAppender();

        assertTrue(Files.exists(deepLogDir.resolve("baresip.log")), "missing parent directories must be created");
    }

    @Test
    void detachStopsWritesToFile() throws Exception {

        BaresipLogConfig.attachFileAppender(logDir);

        BaresipLog.info("before detach");

        BaresipLogConfig.detachFileAppender();

        awaitLogLine(logDir.resolve("baresip.log"), "before detach");

        BaresipLog.info("after detach");

        Thread.sleep(50);

        String content = Files.readString(logDir.resolve("baresip.log"));

        assertFalse(content.contains("after detach"), "a detached file appender must not write");
    }

    @Test
    void reattachAfterDetachWritesAgain() throws Exception {

        BaresipLogConfig.attachFileAppender(logDir);

        BaresipLog.info("first run");

        BaresipLogConfig.detachFileAppender();

        awaitLogLine(logDir.resolve("baresip.log"), "first run");

        BaresipLogConfig.attachFileAppender(logDir);

        BaresipLog.info("second run");

        BaresipLogConfig.detachFileAppender();

        awaitLogLine(logDir.resolve("baresip.log"), "second run");

        String content = Files.readString(logDir.resolve("baresip.log"));

        assertTrue(content.contains("first run") && content.contains("second run"),
                   "re-attach must keep appending to the same file");

        assertFalse(BaresipLogConfig.isFileAppenderAttached(), "detach must leave no appender attached");
    }

    @Test
    void consoleAppenderIsAttachedExactlyOnce() {

        BaresipLogConfig.ensureConsoleAppender();
        BaresipLogConfig.ensureConsoleAppender();

        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("baresip");

        int consoleAppenders = 0;

        for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders(); it.hasNext();) {

            if ("BARESIP_CONSOLE".equals(it.next().getName())) {

                consoleAppenders++;
            }
        }

        assertEquals(1, consoleAppenders, "the console appender must always be active" + " and attached only once");
    }

    @Test
    void ensureDefaultLevelPinsLoggerToInfo() {

        BaresipLogConfig.ensureDefaultLevel();
        BaresipLogConfig.ensureDefaultLevel();

        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("baresip");

        assertEquals(Level.INFO, logger.getLevel(),
                     "the baresip logger must default to INFO" + " and stay there when called repeatedly");
    }

    @Test
    void ensureDefaultLevelPinsJsoftSipLoggerToInfo() {

        BaresipLogConfig.ensureDefaultLevel();
        BaresipLogConfig.ensureDefaultLevel();

        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("jsoftsip");

        assertEquals(Level.INFO, logger.getLevel(),
                     "the jsoftsip logger must default to INFO" + " and stay there when called repeatedly");
    }

    @Test
    void jsoftSipConsoleAppenderIsAttachedExactlyOnce() {

        BaresipLogConfig.ensureJSoftSipConsoleAppender();
        BaresipLogConfig.ensureJSoftSipConsoleAppender();

        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("jsoftsip");

        int consoleAppenders = 0;

        for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders(); it.hasNext();) {

            if ("JSOFTSIP_CONSOLE".equals(it.next().getName())) {

                consoleAppenders++;
            }
        }

        assertEquals(1, consoleAppenders,
                     "the jsoftsip console appender must always be" + " active and attached only once");
    }

    @Test
    void attachJSoftSipFileAppenderWritesWithJsoftSipPattern() throws Exception {

        BaresipLogConfig.attachJSoftSipFileAppender(logDir);

        JSoftSipLog.info("hello jsoftsip");

        BaresipLogConfig.detachJSoftSipFileAppender();

        String line = awaitLogLine(logDir.resolve("jsoftsip.log"), "hello jsoftsip");

        assertTrue(JSOFTSIP_LOG_LINE_PATTERN.matcher(line).matches(),
                   "line must carry the timestamp and [JSOFTSIP]" + " prefix: " + line);
    }

    @Test
    void jsoftSipFileAppenderAttachDetachRoundtrip() throws Exception {

        BaresipLogConfig.attachJSoftSipFileAppender(logDir);

        assertTrue(BaresipLogConfig.isJSoftSipFileAppenderAttached(),
                   "attach must leave the jsoftsip appender attached");

        JSoftSipLog.info("roundtrip");

        BaresipLogConfig.detachJSoftSipFileAppender();

        assertFalse(BaresipLogConfig.isJSoftSipFileAppenderAttached(),
                    "detach must leave no jsoftsip appender attached");

        BaresipLogConfig.detachJSoftSipFileAppender();

        assertFalse(BaresipLogConfig.isJSoftSipFileAppenderAttached(), "detach must be a no-op when already detached");

        awaitLogLine(logDir.resolve("jsoftsip.log"), "roundtrip");
    }

    @Test
    void ensureConfiguredWiresBothLoggers() {

        BaresipLogConfig.ensureConfigured();
        BaresipLogConfig.ensureConfigured();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger baresip = context.getLogger("baresip");

        Logger jsoftsip = context.getLogger("jsoftsip");

        assertEquals(Level.INFO, baresip.getLevel(), "baresip must be pinned to INFO");

        assertEquals(Level.INFO, jsoftsip.getLevel(), "jsoftsip must be pinned to INFO");

        assertTrue(baresip.getAppender("BARESIP_CONSOLE") != null, "the baresip console appender must be attached");

        assertTrue(jsoftsip.getAppender("JSOFTSIP_CONSOLE") != null, "the jsoftsip console appender must be attached");
    }

    private String awaitLogLine(Path file, String fragment) throws IOException, InterruptedException {

        for (int attempt = 0; attempt < 100; attempt++) {

            if (Files.exists(file)) {

                String content = Files.readString(file);

                if (content.contains(fragment)) {

                    return content.lines().filter(line -> line.contains(fragment)).findFirst().orElse("");
                }
            }

            Thread.sleep(5);
        }

        fail("log file never contained: " + fragment);

        return "";
    }
}