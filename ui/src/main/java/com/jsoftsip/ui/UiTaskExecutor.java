package com.jsoftsip.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared executor for UI background tasks. Virtual threads are used
 * because the work is short, mostly IO-bound (pactl, ctrl_tcp dial)
 * and the callers only need fire-and-forget semantics.
 */
public final class UiTaskExecutor {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private UiTaskExecutor() {
    }

    public static ExecutorService global() {
        return EXECUTOR;
    }

    public static void close() {
        EXECUTOR.shutdown();
    }
}
