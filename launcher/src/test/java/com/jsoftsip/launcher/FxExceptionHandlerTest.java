package com.jsoftsip.launcher;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for the global uncaught-exception safety net. When a RuntimeException
 * escapes a JavaFX event-handler thread (e.g. a ctrl_tcp IOException that slipped
 * past the client boundary), the handler must log it and surface an alert
 * instead of silently killing the FX thread.
 */
class FxExceptionHandlerTest {

    private static final RuntimeException EX = new RuntimeException("boom");

    @Test
    void uncaughtExceptionDelegatesToAlertHandler() {

        AtomicReference<Throwable> captured = new AtomicReference<>();

        FxExceptionHandler handler = new FxExceptionHandler(captured::set);

        handler.uncaughtException(Thread.currentThread(), EX);

        assertNotNull(captured.get(), "the alert delegate must receive the throwable");

        assertEquals(EX, captured.get(), "the exact throwable must reach the alert delegate");
    }

    @Test
    void uncaughtExceptionDoesNotThrowItself() {

        // A no-op delegate: the handler must never re-throw, even if
        // the delegate itself is trivial.
        FxExceptionHandler handler = new FxExceptionHandler(t -> {
        });

        // If this throws, the test fails.
        handler.uncaughtException(Thread.currentThread(), EX);

        // If we get here, no exception escaped.
    }

    @Test
    void uncaughtExceptionWithNullDelegateDoesNotThrow() {

        // Even with a null delegate (defensive), the handler must not
        // throw — logging must still happen.
        FxExceptionHandler handler = new FxExceptionHandler(null);

        handler.uncaughtException(Thread.currentThread(), EX);

        // If we get here, no exception escaped.
    }
}
