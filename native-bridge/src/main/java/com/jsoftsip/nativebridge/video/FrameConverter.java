package com.jsoftsip.nativebridge.video;

import java.nio.ByteBuffer;

/**
 * Pure pixel math converters between the wire formats and the
 * BGRA byte order JavaFX consumes.
 *
 * <p>YUV420P is converted with the BT.601 limited-range matrix.
 * Every output buffer is direct so the native memory path can
 * later write into it without an intermediate copy.
 */
public final class FrameConverter {

    private static final int BYTES_PER_PIXEL = 4;

    private FrameConverter() {
    }

    /**
     * Converts a planar YUV420P frame into a tight BGRA buffer.
     * The U and V planes start after the Y plane, each chroma row
     * holds ceil(stride/2) bytes and there are ceil(h/2) of them.
     */
    public static ByteBuffer toBgra(ByteBuffer yuv, int width, int height, int stride) {

        int yPlane = stride * height;
        int uvStride = (stride + 1) / 2;
        int uvRows = (height + 1) / 2;
        int uOffset = yPlane;
        int vOffset = yPlane + uvStride * uvRows;

        ByteBuffer bgra = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL);

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int yIdx = y * stride + x;
                int uIdx = uOffset + (y / 2) * uvStride + x / 2;
                int vIdx = vOffset + (y / 2) * uvStride + x / 2;

                int luma = yuv.get(yIdx) & 0xFF;
                int u = yuv.get(uIdx) & 0xFF;
                int v = yuv.get(vIdx) & 0xFF;

                int c = luma - 16;
                int d = u - 128;
                int e = v - 128;

                int r = clamp((298 * c + 409 * e + 128) >> 8);
                int g = clamp((298 * c - 100 * d - 208 * e + 128) >> 8);
                int b = clamp((298 * c + 516 * d + 128) >> 8);

                int out = (y * width + x) * BYTES_PER_PIXEL;

                bgra.put(out, (byte) b);
                bgra.put(out + 1, (byte) g);
                bgra.put(out + 2, (byte) r);
                bgra.put(out + 3, (byte) 255);
            }
        }

        return bgra;
    }

    /**
     * Copies a logical ARGB32 frame (wire bytes A,R,G,B per pixel)
     * into a tight BGRA buffer, skipping the source row padding.
     */
    public static ByteBuffer copyArgb(ByteBuffer argb, int width, int height, int stride) {

        ByteBuffer bgra = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL);

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int in = y * stride + x * BYTES_PER_PIXEL;
                int out = (y * width + x) * BYTES_PER_PIXEL;

                int a = argb.get(in) & 0xFF;
                int r = argb.get(in + 1) & 0xFF;
                int g = argb.get(in + 2) & 0xFF;
                int b = argb.get(in + 3) & 0xFF;

                bgra.put(out, (byte) b);
                bgra.put(out + 1, (byte) g);
                bgra.put(out + 2, (byte) r);
                bgra.put(out + 3, (byte) a);
            }
        }

        return bgra;
    }

    private static int clamp(int value) {

        return Math.max(0, Math.min(255, value));
    }
}