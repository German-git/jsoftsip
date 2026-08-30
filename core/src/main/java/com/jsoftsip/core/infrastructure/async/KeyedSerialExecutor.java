package com.jsoftsip.core.infrastructure.async;

import com.jsoftsip.core.logging.JSoftSipLog;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes tasks with strict per-key ordering while allowing
 * different keys to progress in parallel. Each key owns a FIFO
 * queue drained by a single worker, so tasks submitted for the
 * same key never run concurrently.
 */
public final class KeyedSerialExecutor implements AutoCloseable {

    private final ConcurrentMap<Long, Queue<Runnable>> queues = new ConcurrentHashMap<>();

    private final ConcurrentMap<Long, AtomicBoolean> draining = new ConcurrentHashMap<>();

    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<Void> submit(long key, Runnable task) {

        CompletableFuture<Void> future = new CompletableFuture<>();

        Queue<Runnable> queue = queues.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>());

        queue.add(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception exception) {
                JSoftSipLog.error("Async task failed for key " + key, exception);
                future.completeExceptionally(exception);
            }
        });

        AtomicBoolean drainFlag = draining.computeIfAbsent(key, ignored -> new AtomicBoolean());

        if (drainFlag.compareAndSet(false, true)) {

            workers.submit(() -> drain(key, queue, drainFlag));
        }

        return future;
    }

    /**
     * Waits until every submitted task has completed. Intended
     * for tests and for close().
     */
    public void awaitIdle() {

        while (!isIdle()) {

            sleepBriefly();
        }

        // One settle pass: a drain that just finished may have
        // re-armed itself for a task that arrived during the
        // check, so confirm stability once more.
        sleepBriefly();

        while (!isIdle()) {

            sleepBriefly();
        }
    }

    private boolean isIdle() {

        return queues.values().stream().allMatch(Queue::isEmpty)
            && draining.values().stream().noneMatch(AtomicBoolean::get);
    }

    private static void sleepBriefly() {

        try {

            Thread.sleep(5);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();
        }
    }

    private void drain(long key, Queue<Runnable> queue, AtomicBoolean drainFlag) {

        try {

            while (true) {

                Runnable task = queue.poll();

                if (task == null) {

                    return;
                }

                task.run();
            }

        } finally {

            drainFlag.set(false);

            if (!queue.isEmpty() && drainFlag.compareAndSet(false, true)) {

                workers.submit(() -> drain(key, queue, drainFlag));
            }
        }
    }

    @Override
    public void close() {

        awaitIdle();

        workers.shutdown();
    }
}