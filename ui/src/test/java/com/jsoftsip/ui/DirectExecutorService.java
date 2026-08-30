package com.jsoftsip.ui;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Test executor that runs every task on the calling thread.
 * Injecting it into {@link MockAppContext} keeps asynchronous
 * controller paths deterministic: the background work finishes
 * before the submit call returns and only the
 * {@code Platform.runLater} hop remains to be flushed.
 */
public final class DirectExecutorService extends AbstractExecutorService {

    @Override
    public void execute(Runnable command) {

        command.run();
    }

    @Override
    public void shutdown() {

        // Nothing to stop: work is always synchronous
    }

    @Override
    public List<Runnable> shutdownNow() {

        return List.of();
    }

    @Override
    public boolean isShutdown() {

        return false;
    }

    @Override
    public boolean isTerminated() {

        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {

        return true;
    }
}
