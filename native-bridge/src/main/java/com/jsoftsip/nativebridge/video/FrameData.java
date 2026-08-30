package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.PixelFormat;

import java.nio.ByteBuffer;

/**
 * One-shot immutable video frame produced by the adapter: owns a
 * direct BGRA buffer ready for JavaFX and is never reused.
 */
final class FrameData implements com.jsoftsip.core.video.VideoFrame {

    private final int width;

    private final int height;

    private final PixelFormat pixelFormat;

    private final long timestampNanos;

    private final int stride;

    private final ByteBuffer buffer;

    FrameData(int width, int height, PixelFormat pixelFormat, long timestampNanos, int stride, ByteBuffer buffer) {

        this.width = width;
        this.height = height;
        this.pixelFormat = pixelFormat;
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
        return pixelFormat;
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