package com.jsoftsip.nativebridge.video;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameConverterTest {

    private static final int BLACK_Y = 16;
    private static final int BLACK_U = 128;
    private static final int BLACK_V = 128;

    /**
     * 4x2 YUV420P frame with stride 8 (4 bytes of padding per row).
     * Planes: Y at 0 (16 bytes), U at 16 (4 bytes), V at 20 (4 bytes).
     */
    private static ByteBuffer yuv420p4x2() {

        int stride = 8;
        int yPlane = stride * 2;
        int uvPlane = (stride / 2) * (2 / 2);

        ByteBuffer yuv = ByteBuffer.allocate(yPlane + 2 * uvPlane);

        for (int i = 0; i < yuv.capacity(); i++) {
            yuv.put(i, (byte) BLACK_Y);
        }

        for (int i = yPlane; i < yuv.capacity(); i++) {
            yuv.put(i, (byte) BLACK_U);
        }

        // keep U = V = 128 so chroma contributes nothing
        return yuv;
    }

    @Test
    void convertsAllBlackToOpaqueBlackPixels() {

        ByteBuffer bgra = FrameConverter.toBgra(yuv420p4x2(), 4, 2, 8);

        assertEquals(4 * 2 * 4, bgra.remaining());
        assertTrue(bgra.isDirect());

        for (int pixel = 0; pixel < 8; pixel++) {
            int offset = pixel * 4;
            assertEquals((byte) 0, bgra.get(offset), "B");
            assertEquals((byte) 0, bgra.get(offset + 1), "G");
            assertEquals((byte) 0, bgra.get(offset + 2), "R");
            assertEquals((byte) 255, bgra.get(offset + 3), "A");
        }
    }

    @Test
    void convertsWhiteLumaToOpaqueWhitePixels() {

        ByteBuffer yuv = yuv420p4x2();
        yuv.put(0, (byte) 235);

        ByteBuffer bgra = FrameConverter.toBgra(yuv, 4, 2, 8);

        assertEquals((byte) 255, bgra.get(0), "B");
        assertEquals((byte) 255, bgra.get(1), "G");
        assertEquals((byte) 255, bgra.get(2), "R");
        assertEquals((byte) 255, bgra.get(3), "A");

        // the rest stays black
        assertEquals((byte) 0, bgra.get(4), "next pixel must stay black");
    }

    @Test
    void mapsLumaRowsAtTheStrideOffset() {

        ByteBuffer yuv = yuv420p4x2();
        // last pixel of the second row lives at row 1 byte 3
        yuv.put(8 + 3, (byte) 235);

        ByteBuffer bgra = FrameConverter.toBgra(yuv, 4, 2, 8);

        // pixel (3,1) is white, pixel (0,0) stays black
        assertEquals((byte) 0, bgra.get(0));
        assertEquals((byte) 255, bgra.get(7 * 4));
    }

    @Test
    void convertsMidGrayLumaToMidGrayPixels() {

        ByteBuffer yuv = yuv420p4x2();
        yuv.put(0, (byte) 128);

        ByteBuffer bgra = FrameConverter.toBgra(yuv, 4, 2, 8);

        assertEquals((byte) 130, bgra.get(0), "B");
        assertEquals((byte) 130, bgra.get(1), "G");
        assertEquals((byte) 130, bgra.get(2), "R");
    }

    @Test
    void chromaDrivesTheColorChannels() {

        // Y=82, U=90, V=240 renders the classic red test signal
        ByteBuffer yuv = yuv420p4x2();
        yuv.put(0, (byte) 82);
        yuv.put(16, (byte) 90); // U plane start
        yuv.put(20, (byte) 240); // V plane start

        ByteBuffer bgra = FrameConverter.toBgra(yuv, 4, 2, 8);

        assertEquals((byte) 0, bgra.get(0), "B");
        assertEquals((byte) 1, bgra.get(1), "G");
        assertEquals((byte) 255, bgra.get(2), "R");
    }

    @Test
    void copiesArgbPixelsIntoBgraByteOrder() {

        // logical ARGB32 wire bytes are A,R,G,B per pixel
        ByteBuffer argb = ByteBuffer.allocate(4);
        argb.put((byte) 255); // A
        argb.put((byte) 10); // R
        argb.put((byte) 20); // G
        argb.put((byte) 30); // B

        ByteBuffer bgra = FrameConverter.copyArgb(argb, 1, 1, 4);

        assertEquals(4, bgra.remaining());
        assertTrue(bgra.isDirect());
        assertEquals((byte) 30, bgra.get(0), "B");
        assertEquals((byte) 20, bgra.get(1), "G");
        assertEquals((byte) 10, bgra.get(2), "R");
        assertEquals((byte) 255, bgra.get(3), "A");
    }

    @Test
    void copyArgbSkipsRowPadding() {

        // 1x2 frame with stride 8: each row is 4 bytes + 4 padding
        ByteBuffer argb = ByteBuffer.allocate(16);
        argb.put(0, (byte) 255); // row 0 alpha
        argb.put(1, (byte) 1); // row 0 red
        argb.put(2, (byte) 2); // row 0 green
        argb.put(3, (byte) 3); // row 0 blue
        argb.put(8, (byte) 255); // row 1 alpha
        argb.put(9, (byte) 40); // row 1 red
        argb.put(10, (byte) 50); // row 1 green
        argb.put(11, (byte) 60); // row 1 blue

        ByteBuffer bgra = FrameConverter.copyArgb(argb, 1, 2, 8);

        assertEquals(8, bgra.remaining());
        assertEquals((byte) 3, bgra.get(0));
        assertEquals((byte) 60, bgra.get(4));
    }
}