package com.jsoftsip.core.video;

/**
 * Pixel formats supported by the video frame pipe.
 *
 * <p>The ordinal value of each constant is the byte sent on the wire by the
 * native module, so it is part of the protocol and must never be reordered.
 */
public enum PixelFormat {

    /** Planar YUV 4:2:0 as produced by a baresip video source. */
    YUV420P,

    /** Logical ARGB32 (4 bytes per pixel) as consumed by the UI. */
    ARGB32
}