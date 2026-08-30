package com.jsoftsip.core.video;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePipeTest {

    private static VideoFrame frame(int id) {

        return new VideoFrame() {

            private final int frameId = id;

            @Override
            public int width() {
                return 4;
            }

            @Override
            public int height() {
                return 2;
            }

            @Override
            public PixelFormat pixelFormat() {
                return PixelFormat.ARGB32;
            }

            @Override
            public long timestampNanos() {
                return frameId;
            }

            @Override
            public int stride() {
                return 16;
            }

            @Override
            public ByteBuffer buffer() {
                return ByteBuffer.allocate(32);
            }
        };
    }

    @Test
    void startsEmptyAndPollReturnsNull() {

        FramePipe pipe = new FramePipe();

        assertEquals(3, pipe.capacity());
        assertNull(pipe.poll());
    }

    @Test
    void drainsInFifoOrder() {

        FramePipe pipe = new FramePipe();

        pipe.push(frame(1));
        pipe.push(frame(2));
        pipe.push(frame(3));

        assertEquals(1L, pipe.poll().timestampNanos());
        assertEquals(2L, pipe.poll().timestampNanos());
        assertEquals(3L, pipe.poll().timestampNanos());
        assertNull(pipe.poll());
    }

    @Test
    void pushingBeyondCapacityKeepsOnlyTheNewestThree() {

        FramePipe pipe = new FramePipe();

        for (int id = 1; id <= 4; id++) {
            pipe.push(frame(id));
        }

        assertEquals(2L, pipe.poll().timestampNanos());
        assertEquals(3L, pipe.poll().timestampNanos());
        assertEquals(4L, pipe.poll().timestampNanos());
        assertNull(pipe.poll());
    }

    @Test
    void hundredFrameBurstKeepsTheNewestThree() {

        FramePipe pipe = new FramePipe();

        for (int id = 1; id <= 100; id++) {
            pipe.push(frame(id));
        }

        assertEquals(98L, pipe.poll().timestampNanos());
        assertEquals(99L, pipe.poll().timestampNanos());
        assertEquals(100L, pipe.poll().timestampNanos());
        assertNull(pipe.poll());
    }

    @Test
    void concurrentPushAndPollNeverBreaksTheRing() throws Exception {

        FramePipe pipe = new FramePipe();

        CountDownLatch pushersDone = new CountDownLatch(2);

        Thread pusher1 = new Thread(() -> {
            for (int id = 0; id < 500; id++) {
                pipe.push(frame(id));
            }
            pushersDone.countDown();
        });

        Thread pusher2 = new Thread(() -> {
            for (int id = 1000; id < 1500; id++) {
                pipe.push(frame(id));
            }
            pushersDone.countDown();
        });

        Thread drainer = new Thread(() -> {
            // drain while producers are alive, then empty the
            // at most three surviving slots: termination is
            // guaranteed by the latch, not by a success count
            while (pushersDone.getCount() > 0) {
                pipe.poll();
                Thread.onSpinWait();
            }
            while (pipe.poll() != null) {
                // survivors drained
            }
        });

        pusher1.start();
        pusher2.start();
        drainer.start();
        pusher1.join();
        pusher2.join();
        pushersDone.await();
        drainer.join();

        // The ring must be empty and structurally intact after
        // the contention stress.
        assertNull(pipe.poll());
    }

    @Test
    void pollNullsTheSlotSoTheBufferCanBeGarbageCollected() throws Exception {

        FramePipe pipe = new FramePipe();
        pipe.push(frame(1));

        VideoFrame polled = pipe.poll();
        assertSame(frame(1).getClass(), polled.getClass());

        Field ringField = FramePipe.class.getDeclaredField("ring");
        ringField.setAccessible(true);
        VideoFrame[] ring = (VideoFrame[]) ringField.get(pipe);

        for (VideoFrame slot : ring) {
            assertNull(slot, "every polled or overwritten slot must be nulled");
        }
    }

    @Test
    void shutdownClearsEverySlotAndStopsPolls() throws Exception {

        FramePipe pipe = new FramePipe();
        pipe.push(frame(1));
        pipe.push(frame(2));
        pipe.push(frame(3));

        pipe.shutdown();

        Field ringField = FramePipe.class.getDeclaredField("ring");
        ringField.setAccessible(true);
        VideoFrame[] ring = (VideoFrame[]) ringField.get(pipe);

        for (VideoFrame slot : ring) {
            assertNull(slot, "shutdown must null every slot for GC");
        }

        assertNull(pipe.poll());
        assertTrue(pipe.isShutdown());
    }

    @Test
    void pushAfterShutdownIsIgnored() {

        FramePipe pipe = new FramePipe();
        pipe.shutdown();

        pipe.push(frame(1));

        assertNull(pipe.poll());
    }

    @Test
    void drainedPipeCanBeRefilled() {

        FramePipe pipe = new FramePipe();
        pipe.push(frame(1));
        pipe.push(frame(2));
        pipe.poll();
        pipe.poll();

        pipe.push(frame(3));

        assertEquals(3L, pipe.poll().timestampNanos());
        assertNull(pipe.poll());
    }

    @Test
    void saturationTracksTheRingFillLevel() {

        // Producers need a cheap way to know the
        // next push would overwrite a buffered frame, so expensive
        // conversions can be skipped while the consumer is behind.
        FramePipe pipe = new FramePipe();

        assertTrue(!pipe.isSaturated(), "an empty ring is not saturated");

        pipe.push(frame(1));

        assertTrue(!pipe.isSaturated(), "one buffered frame leaves room");

        pipe.push(frame(2));
        pipe.push(frame(3));

        assertTrue(pipe.isSaturated(), "a full ring must report saturation");
    }

    @Test
    void saturatedReportsTrueAfterShutdown() {

        FramePipe pipe = new FramePipe();

        pipe.shutdown();

        assertTrue(pipe.isSaturated(), "a shut down pipe must look saturated so producers bail out early");
    }
}