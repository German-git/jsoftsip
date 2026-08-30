package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.PixelFormat;

/**
 * Callback receiving every frame decoded from the wire, so the
 * transport stays independent of the routing implementation.
 * Implementations must be safe to call concurrently from the
 * reader threads and must never throw into the transport.
 */
public interface FrameRouter {

    /**
     * Routes one decoded frame. The pixels array is owned by the
     * transport and must not be retained beyond the call.
     */
    void route(String aor, int width, int height, PixelFormat pixelFormat, long timestampNanos, int stride,
               byte[] pixels);
}