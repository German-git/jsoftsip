package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.PixelFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Parses the video frame wire protocol spoken by the custom
 * baresip vidisp module over TCP loopback.
 *
 * <p>Layout per frame: 2 bytes big-endian AOR length, the UTF-8
 * AOR bytes, 4 bytes big-endian payload length (21 + pixelBytes),
 * then the 21 byte header (width, height, pixelFormat, timestamp,
 * stride) and the raw pixel data.
 *
 * <p>{@link #read(InputStream)} returns an empty Optional on a
 * clean EOF at a frame boundary and throws
 * {@link WireFormatException} on truncated or malformed data, so
 * the transport can close the offending socket and re-accept.
 */
public final class FrameWireReader {

    private static final int HEADER_BYTES = 21;

    /**
     * Upper bound on a declared frame length. Real frames top
     * out around a few MB even at 4K, so any length beyond this
     * cap means the header is corrupt and honoring it could
     * allocate up to two gigabytes before a single payload byte
     * ever arrives.
     */
    static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

    private FrameWireReader() {
    }

    /**
     * Reads one frame, or empty when the stream ends cleanly
     * before the next frame starts.
     */
    public static Optional<WireFrame> read(InputStream in) throws IOException {

        byte[] aorBytes = readRouting(in);

        if (aorBytes == null) {
            return Optional.empty();
        }

        String aor = new String(aorBytes, StandardCharsets.UTF_8);

        byte[] lengthBytes = readFully(in, 4, "frame length");

        if (lengthBytes == null) {
            throw new WireFormatException("stream ended before the frame length");
        }

        int length = ((lengthBytes[0] & 0xFF) << 24) | ((lengthBytes[1] & 0xFF) << 16) | ((lengthBytes[2] & 0xFF) << 8)
            | (lengthBytes[3] & 0xFF);

        if (length < HEADER_BYTES) {
            throw new WireFormatException("frame length " + length + " is shorter than the header");
        }

        // Reject before readFully: the cap check must precede any
        // allocation so a corrupt header can never drive a
        // multi-gigabyte array.
        if (length > MAX_FRAME_BYTES) {
            throw new WireFormatException("frame length " + length + " exceeds the cap of " + MAX_FRAME_BYTES);
        }

        byte[] frameBytes = readFully(in, length, "frame body");

        if (frameBytes == null) {
            throw new WireFormatException("stream ended inside a frame body");
        }

        int width = intAt(frameBytes, 0);
        int height = intAt(frameBytes, 4);

        if (width <= 0 || height <= 0) {
            throw new WireFormatException("frame dimensions must be positive: " + width + "x" + height);
        }

        int formatOrdinal = frameBytes[8] & 0xFF;

        if (formatOrdinal < 0 || formatOrdinal >= PixelFormat.values().length) {
            throw new WireFormatException("unknown pixel format ordinal " + formatOrdinal);
        }

        long timestampNanos = longAt(frameBytes, 9);
        int stride = intAt(frameBytes, 17);

        if (stride <= 0) {
            throw new WireFormatException("frame stride must be positive: " + stride);
        }

        byte[] pixels = new byte[length - HEADER_BYTES];
        System.arraycopy(frameBytes, HEADER_BYTES, pixels, 0, pixels.length);

        return Optional.of(new WireFrame(aor, width, height, PixelFormat.values()[formatOrdinal], timestampNanos,
            stride, pixels));
    }

    /**
     * Reads the 2 byte AOR length plus the AOR itself. Returns
     * null when the stream ends before the AOR length, which is
     * the only clean EOF point.
     */
    private static byte[] readRouting(InputStream in) throws IOException {

        byte[] lenBytes = readFully(in, 2, "AOR length");

        if (lenBytes == null) {
            return null;
        }

        int aorLength = ((lenBytes[0] & 0xFF) << 8) | (lenBytes[1] & 0xFF);

        byte[] aor = readFully(in, aorLength, "AOR");

        if (aor == null) {
            throw new WireFormatException("stream ended inside the AOR");
        }

        return aor;
    }

    /**
     * Reads exactly n bytes, or null when the stream ends before
     * any byte of this read arrives. A stream that ends mid-read
     * is a truncated frame and throws.
     */
    private static byte[] readFully(InputStream in, int n, String what) throws IOException {

        byte[] bytes = new byte[n];
        int read = 0;

        while (read < n) {

            int count = in.read(bytes, read, n - read);

            if (count < 0) {

                if (read == 0) {
                    return null;
                }

                throw new WireFormatException("stream ended inside " + what);
            }

            read += count;
        }

        return bytes;
    }

    private static int intAt(byte[] bytes, int offset) {

        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16) | ((bytes[offset + 2] & 0xFF) << 8)
            | (bytes[offset + 3] & 0xFF);
    }

    private static long longAt(byte[] bytes, int offset) {

        return ((long) (bytes[offset] & 0xFF) << 56) | ((long) (bytes[offset + 1] & 0xFF) << 48)
            | ((long) (bytes[offset + 2] & 0xFF) << 40) | ((long) (bytes[offset + 3] & 0xFF) << 32)
            | ((long) (bytes[offset + 4] & 0xFF) << 24) | ((long) (bytes[offset + 5] & 0xFF) << 16)
            | ((long) (bytes[offset + 6] & 0xFF) << 8) | (bytes[offset + 7] & 0xFF);
    }

    /**
     * One decoded frame from the wire: the routing AOR plus the
     * frame metadata and raw pixel bytes.
     */
    public record WireFrame(String aor, int width, int height, PixelFormat pixelFormat, long timestampNanos, int stride,
        byte[] pixels) {
    }
}