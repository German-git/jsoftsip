package com.jsoftsip.core.video;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract test for the producer-facing source API. The native
 * implementation lives in native-bridge (FramePipeAdapter), the
 * real per-account isolation is verified there too.
 */
class VideoFrameSourceTest {

    /**
     * Test double mirroring the documented contract: pipes are
     * created per account via computeIfAbsent and setQuality is
     * prepared but unsupported in the native implementation.
     */
    private static final class StubSource implements VideoFrameSource {

        private final ConcurrentHashMap<Long, FramePipe> pipes = new ConcurrentHashMap<>();

        @Override
        public FramePipe getOrCreateFramePipe(long accountId) {

            return pipes.computeIfAbsent(accountId, ignored -> new FramePipe());
        }

        @Override
        public void setQuality(long accountId, VideoQuality quality) {

            throw new UnsupportedOperationException("setQuality is prepared but not implemented");
        }
    }

    @Test
    void returnsTheSamePipeForTheSameAccount() {

        VideoFrameSource source = new StubSource();

        assertSame(source.getOrCreateFramePipe(7), source.getOrCreateFramePipe(7));
    }

    @Test
    void createsOnePipePerAccountWithoutCrossLeak() {

        VideoFrameSource source = new StubSource();

        FramePipe alice = source.getOrCreateFramePipe(7);
        FramePipe bob = source.getOrCreateFramePipe(9);

        assertNotSame(alice, bob);

        // Frames pushed for alice must never be visible to bob
        alice.push(new VideoFrame() {

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
                return 1L;
            }

            @Override
            public int stride() {
                return 16;
            }

            @Override
            public java.nio.ByteBuffer buffer() {
                return java.nio.ByteBuffer.allocate(32);
            }
        });

        assertNull(bob.poll());
    }

    @Test
    void setQualityIsPreparedButUnsupported() {

        VideoFrameSource source = new StubSource();

        assertThrows(UnsupportedOperationException.class, () -> source.setQuality(7, new VideoQuality(640, 480)));
    }
}