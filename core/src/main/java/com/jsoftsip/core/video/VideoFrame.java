package com.jsoftsip.core.video;

import java.nio.ByteBuffer;

/**
 * One immutable video frame travelling through the frame pipe.
 *
 * <p>Implementations must treat the returned buffer as read-only: consumers
 * may read it while the producer keeps writing into the next frame.
 */
public interface VideoFrame {

    /** Frame width in pixels. */
    int width();

    /** Frame height in pixels. */
    int height();

    /** Pixel format of {@link #buffer()}. */
    PixelFormat pixelFormat();

    /** Capture timestamp in nanoseconds. */
    long timestampNanos();

    /** Number of bytes between the start of two consecutive rows. */
    int stride();

    /** Pixel data. May be a direct buffer, so consumers must not call array(). */
    ByteBuffer buffer();
}