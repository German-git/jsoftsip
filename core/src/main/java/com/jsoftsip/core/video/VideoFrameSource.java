package com.jsoftsip.core.video;

/**
 * Producer-facing entry point for video frames, implemented by
 * the native-bridge adapter and consumed by the UI through
 * AppContext.
 *
 * <p>Every account owns a distinct {@link FramePipe}, so frames
 * never cross accounts.
 */
public interface VideoFrameSource {

    /**
     * Returns the frame pipe of the given account, creating it
     * on first use. The returned pipe must be safe to call
     * concurrently from the transport reader thread and the
     * FX thread.
     */
    FramePipe getOrCreateFramePipe(long accountId);

    /**
     * Requests a capture resolution for the account. The native
     * implementation prepares the API but does not negotiate
     * codecs over the control channel yet, so it throws
     * {@link UnsupportedOperationException}.
     */
    void setQuality(long accountId, VideoQuality quality);
}