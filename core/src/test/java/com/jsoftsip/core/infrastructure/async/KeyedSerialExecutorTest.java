package com.jsoftsip.core.infrastructure.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class KeyedSerialExecutorTest {

    @Test
    void submitReturnsFutureCompletedOnSuccess() throws Exception {
        try (KeyedSerialExecutor executor = new KeyedSerialExecutor()) {
            AtomicInteger counter = new AtomicInteger();
            CompletableFuture<Void> future = executor.submit(1L, counter::incrementAndGet);
            future.get();
            assertEquals(1, counter.get());
        }
    }

    @Test
    void submitPropagatesFailureThroughFuture() throws Exception {
        try (KeyedSerialExecutor executor = new KeyedSerialExecutor()) {
            CompletableFuture<Void> future = executor.submit(1L, () -> {
                throw new IllegalStateException("boom");
            });
            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertEquals("boom", exception.getCause().getMessage());
        }
    }

    @Test
    void tasksForSameKeyRunInSubmissionOrder() throws Exception {
        try (KeyedSerialExecutor executor = new KeyedSerialExecutor()) {
            List<Integer> order = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int value = i;
                futures.add(executor.submit(1L, () -> order.add(value)));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            assertEquals(20, order.size());
            for (int i = 0; i < 20; i++) {
                assertEquals(i, order.get(i));
            }
        }
    }

    @Test
    void differentKeysProgressIndependently() throws Exception {
        try (KeyedSerialExecutor executor = new KeyedSerialExecutor()) {
            AtomicInteger keyOne = new AtomicInteger();
            AtomicInteger keyTwo = new AtomicInteger();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(1L, keyOne::incrementAndGet));
                futures.add(executor.submit(2L, keyTwo::incrementAndGet));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            assertEquals(10, keyOne.get());
            assertEquals(10, keyTwo.get());
        }
    }

    @Test
    void awaitIdleReturnsAfterTasksComplete() throws Exception {
        KeyedSerialExecutor executor = new KeyedSerialExecutor();
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            executor.submit(1L, counter::incrementAndGet);
        }
        executor.awaitIdle();
        executor.close();
        assertTrue(counter.get() >= 5);
    }
}
