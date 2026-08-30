package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.video.FramePipe;
import com.jsoftsip.core.video.PixelFormat;
import com.jsoftsip.core.video.VideoFrame;
import com.jsoftsip.core.video.VideoFrameSource;
import com.jsoftsip.core.video.VideoQuality;
import com.jsoftsip.nativebridge.baresip.BaresipLog;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production {@link VideoFrameSource}: routes wire frames by AOR
 * to the owning account pipe and converts pixels into the BGRA
 * byte order JavaFX consumes.
 *
 * <p>The transport delivers decoded frames through the
 * {@link FrameRouter} callback, unknown AORs are dropped with a
 * debug log so a stale module can never crash the app.
 */
public final class FramePipeAdapter implements VideoFrameSource, FrameRouter {

    private final SipClient sipClient;

    private final ConcurrentHashMap<Long, FramePipe> pipes = new ConcurrentHashMap<>();

    public FramePipeAdapter(SipClient sipClient) {

        this.sipClient = sipClient;
    }

    @Override
    public FramePipe getOrCreateFramePipe(long accountId) {

        return pipes.computeIfAbsent(accountId, ignored -> new FramePipe());
    }

    @Override
    public void setQuality(long accountId, VideoQuality quality) {

        // prepared API only: codec negotiation over the control
        // channel is out of scope for this change
        throw new UnsupportedOperationException("setQuality is prepared but not implemented");
    }

    @Override
    public void route(String aor, int width, int height, PixelFormat pixelFormat, long timestampNanos, int stride,
                      byte[] pixels) {

        Long accountId = sipClient.accountIdForAor(aor);

        if (accountId == null) {

            BaresipLog.debug("Dropping video frame for unknown AOR " + aor);

            return;
        }

        FramePipe pipe = getOrCreateFramePipe(accountId);

        // The conversion below is per-pixel CPU
        // work plus a fresh direct buffer. When the consumer is
        // behind (ring full) or gone (shut down), the converted
        // frame would only displace an older one or be dropped, so
        // skip it entirely and let the next frame win once the
        // consumer catches up.
        if (pipe.isSaturated()) {

            BaresipLog.debug("Skipping video frame for saturated pipe of account " + accountId);

            return;
        }

        ByteBuffer bgra = switch (pixelFormat) {
            case YUV420P -> FrameConverter.toBgra(ByteBuffer.wrap(pixels), width, height, stride);
            case ARGB32 -> FrameConverter.copyArgb(ByteBuffer.wrap(pixels), width, height, stride);
        };

        VideoFrame frame = new FrameData(width, height, PixelFormat.ARGB32, timestampNanos, width * 4, bgra);

        pipe.push(frame);
    }

    /**
     * Shuts down every pipe so buffered frames become
     * garbage-collectible. The transport lifecycle is owned by
     * the composition root. Pipes stay registered so the
     * account-to-pipe identity remains stable after shutdown.
     */
    public void shutdown() {

        pipes.values().forEach(FramePipe::shutdown);
    }
}