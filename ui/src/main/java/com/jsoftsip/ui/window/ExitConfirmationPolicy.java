package com.jsoftsip.ui.window;

/**
 * JavaFX-free decision for the main-window close flow:
 * given the persisted user preference and the current
 * active call count, should the app ask before exiting?
 */
public final class ExitConfirmationPolicy {

    private ExitConfirmationPolicy() {
    }

    public static boolean shouldConfirmExit(boolean confirmEnabled, int activeCallCount) {

        return confirmEnabled && activeCallCount > 0;
    }
}
