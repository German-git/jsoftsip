package com.jsoftsip.nativebridge.video;

/**
 * Signals a protocol violation in the video frame wire stream:
 * truncated data, a payload shorter than the header, unknown
 * pixel formats or non-positive dimensions. The transport closes
 * the offending socket and re-accepts instead of crashing.
 */
public class WireFormatException extends RuntimeException {

    public WireFormatException(String message) {

        super(message);
    }
}