package com.jsoftsip.ui.dialog;

/**
 * Minimal, testable handle over a window that owns its
 * lifecycle. Both DialerDialog and DialerWindowManager attach
 * their window-hidden callbacks through it, so they never
 * overwrite each other (unlike setOnHidden, which would
 * silently drop the first handler and leak the call listener).
 */
public interface WindowHandle {

    void addWindowHiddenHandler(Runnable handler);

    void close();
}
