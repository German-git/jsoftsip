package com.jsoftsip.core.video;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VideoFrameTest {

    private static final class StubFrame implements VideoFrame {

        private final int width;
        private final int height;
        private final PixelFormat format;
        private final long timestampNanos;
        private final int stride;
        private final ByteBuffer buffer;

        StubFrame(int width, int height, PixelFormat format, long timestampNanos, int stride, ByteBuffer buffer) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.timestampNanos = timestampNanos;
            this.stride = stride;
            this.buffer = buffer;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public PixelFormat pixelFormat() {
            return format;
        }

        @Override
        public long timestampNanos() {
            return timestampNanos;
        }

        @Override
        public int stride() {
            return stride;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }
    }

    @Test
    void exposesItsMetadataAndPixelBuffer() {

        ByteBuffer pixels = ByteBuffer.allocate(64);

        VideoFrame frame = new StubFrame(16, 9, PixelFormat.ARGB32, 1_234_567_890L, 64, pixels);

        assertEquals(16, frame.width());
        assertEquals(9, frame.height());
        assertEquals(PixelFormat.ARGB32, frame.pixelFormat());
        assertEquals(1_234_567_890L, frame.timestampNanos());
        assertEquals(64, frame.stride());
        assertSame(pixels, frame.buffer());
    }

    @Test
    void supportsYuv420pFrames() {

        VideoFrame frame = new StubFrame(4, 2, PixelFormat.YUV420P, 0L, 4, ByteBuffer.allocate(12));

        assertEquals(PixelFormat.YUV420P, frame.pixelFormat());
        assertEquals(12, frame.buffer().capacity());
    }
}