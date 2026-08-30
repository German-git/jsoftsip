package com.jsoftsip.launcher;

import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.dialog.DialogService;
import javafx.application.Platform;

import java.util.function.Consumer;

/**
 * Global safety net for uncaught exceptions on the JavaFX thread.
 *
 * <p>When the ctrl_tcp connection dies, IO failures that slip past the
 * SIP-client boundary (e.g. a RuntimeException that escaped a button
 * handler) would silently kill the FX event-handler thread — the same
 * "button does nothing" symptom as the dialer hangup bug. This handler
 * logs the exception and surfaces an error alert so the user at least
 * knows something went wrong.</p>
 *
 * <p>Installed in {@link JSoftSipApplication#start} via {@link #install()}.
 * The handler is wired through {@code Thread.setDefaultUncaughtExceptionHandler},
 * which catches exceptions thrown from any thread's event-dispatch — including
 * the JavaFX Application Thread's event handlers (button clicks, menu
 * selections, etc.). Exceptions from {@code Platform.runLater} callbacks are
 * also routed through this handler.</p>
 *
 * <p>The alert delegate is injectable so tests can verify behavior without
 * touching the JavaFX thread.</p>
 */
public class FxExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * Displays an error alert for the given throwable. In production this
     * runs on the FX thread via Platform.runLater, in tests it is replaced
     * with a capture consumer.
     */
    @FunctionalInterface
    public interface AlertDelegate extends Consumer<Throwable> {
    }

    private final AlertDelegate alertDelegate;

    public FxExceptionHandler(AlertDelegate alertDelegate) {

        this.alertDelegate = alertDelegate;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {

        JSoftSipLog.error("Uncaught exception on " + thread.getName(), throwable);

        if (alertDelegate != null) {

            alertDelegate.accept(throwable);
        }
    }

    /**
     * Installs the global handler on both the default uncaught-exception
     * path and the JavaFX Platform exception path. Must be called from
     * the FX thread during application startup.
     */
    public static void install() {

        FxExceptionHandler handler = new FxExceptionHandler(throwable -> {

            Platform.runLater(() -> {

                DialogService.showError(null, I18n.get("error.uncaught.title"), I18n.get("error.uncaught.header"),
                                        I18n.format("error.uncaught.content", throwable.getMessage()));
            });
        });

        Thread.setDefaultUncaughtExceptionHandler(handler);

        JSoftSipLog.info("Installed global FX uncaught-exception handler");
    }
}
