package com.jsoftsip.nativebridge.baresip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipLogTest {

    @Test
    void sanitizeStripsAnsiColorsAndTrims() {

        assertEquals("Hello", BaresipLog.sanitize("\u001B[31mHello\u001B[0m"),
                     "colored output must lose its SGR escapes");

        assertEquals("padded", BaresipLog.sanitize("  \u001B[1;32m padded \u001B[0m  "),
                     "leading/trailing whitespace must be trimmed");
    }

    @Test
    void sanitizeKeepsPlainLinesTrimmed() {

        assertEquals("plain line", BaresipLog.sanitize("  plain line  "), "plain lines must only be trimmed");

        assertEquals("", BaresipLog.sanitize(" \u001B[0m "), "an all-escape line must sanitize to empty");
    }

    @Test
    void sanitizeSecretsRedactsValueInTheMiddleOfParams() {

        assertEquals("sip:1003@host;transport=udp;auth_pass=***;x=y",
                     BaresipLog.sanitizeSecrets("sip:1003@host;transport=udp;auth_pass=1234;x=y"),
                     "middle auth_pass value must be redacted");
    }

    @Test
    void sanitizeSecretsRedactsValueAtEndOfString() {

        assertEquals("sip:1003@host;transport=udp;auth_pass=***",
                     BaresipLog.sanitizeSecrets("sip:1003@host;transport=udp;auth_pass=1234"),
                     "trailing auth_pass value must be redacted");
    }

    @Test
    void sanitizeSecretsRedactsPasswordAndSecretVariants() {

        assertEquals("password=***;secret=***", BaresipLog.sanitizeSecrets("password=x;secret=y"),
                     "password and secret values must be redacted");

        assertEquals("AUTH_PASS=***;Password=***;SECRET=***",
                     BaresipLog.sanitizeSecrets("AUTH_PASS=zz;Password=yy;SECRET=xx"),
                     "case-insensitive keys must be redacted");
    }

    @Test
    void sanitizeSecretsLeavesMessagesWithoutSecretsUnchanged() {

        String clean = "sip:1003@host;transport=udp;regint=600";

        assertEquals(clean, BaresipLog.sanitizeSecrets(clean), "messages without secrets must stay unchanged");
    }

    @Test
    void infoRedactsSecretsFromLoggedCommands() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.info("COMMAND -> uanew sip:1003@192.168.0.97" + ";transport=udp;auth_pass=1003aa1003");

            String message = appender.list.get(0).getFormattedMessage();

            assertTrue(message.contains("auth_pass=***"), "logged command must keep the key with redacted value");

            assertFalse(message.contains("1003aa1003"), "logged command must not leak the plaintext secret");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void debugEmitsAtDebugLevelAndRedactsSecrets() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.debug("DEBUG uanew sip:1003@host;auth_pass=1234");

            ILoggingEvent event = appender.list.get(0);

            assertEquals(Level.DEBUG, event.getLevel(), "debug must emit at DEBUG level");

            assertTrue(event.getFormattedMessage().contains("auth_pass=***"), "debug must redact secrets");

            assertFalse(event.getFormattedMessage().contains("1234"), "debug must not leak the plaintext secret");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void warnEmitsAtWarnLevelAndRedactsSecrets() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.warn("WARN uanew sip:1003@host;auth_pass=1234");

            ILoggingEvent event = appender.list.get(0);

            assertEquals(Level.WARN, event.getLevel(), "warn must emit at WARN level");

            assertTrue(event.getFormattedMessage().contains("auth_pass=***"), "warn must redact secrets");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void errorEmitsAtErrorLevelAndRedactsSecrets() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.error("ERROR uanew sip:1003@host;auth_pass=1234");

            ILoggingEvent event = appender.list.get(0);

            assertEquals(Level.ERROR, event.getLevel(), "error must emit at ERROR level");

            assertTrue(event.getFormattedMessage().contains("auth_pass=***"), "error must redact secrets");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void warnWithThrowableCarriesThrowableAndRedactsSecrets() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.warn("WARN uanew sip:1003@host;auth_pass=1234", new IllegalStateException("boom"));

            ILoggingEvent event = appender.list.get(0);

            assertEquals(Level.WARN, event.getLevel(), "warn with throwable must emit at WARN level");

            assertTrue(event.getThrowableProxy() != null, "warn with throwable must attach the throwable");

            assertTrue(event.getFormattedMessage().contains("auth_pass=***"),
                       "warn with throwable must redact secrets");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void errorWithThrowableCarriesThrowableAndRedactsSecrets() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.error("ERROR uanew sip:1003@host;auth_pass=1234", new IllegalStateException("boom"));

            ILoggingEvent event = appender.list.get(0);

            assertEquals(Level.ERROR, event.getLevel(), "error with throwable must emit at ERROR level");

            assertTrue(event.getThrowableProxy() != null, "error with throwable must attach the throwable");

            assertTrue(event.getFormattedMessage().contains("auth_pass=***"),
                       "error with throwable must redact secrets");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void debugIsNotEmittedAtDefaultInfoLevel() {

        BaresipLogConfig.ensureDefaultLevel();

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            BaresipLog.debug("DEBUG must be filtered at INFO");

            assertTrue(appender.list.isEmpty(), "debug must not reach the appender" + " while the level is INFO");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
        }
    }
}