package com.jsoftsip.core.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PixelFormatTest {

    @Test
    void exposesExactlyTheTwoSupportedFormats() {

        assertEquals(2, PixelFormat.values().length);
        assertEquals(PixelFormat.YUV420P, PixelFormat.valueOf("YUV420P"));
        assertEquals(PixelFormat.ARGB32, PixelFormat.valueOf("ARGB32"));
    }

    @Test
    void wireOrdinalOfYuv420pIsZero() {

        assertEquals(0, PixelFormat.YUV420P.ordinal());
    }

    @Test
    void wireOrdinalOfArgb32IsOne() {

        assertEquals(1, PixelFormat.ARGB32.ordinal());
    }

    @Test
    void ordinalToEnumRoundTripIsStable() {

        for (PixelFormat format : PixelFormat.values()) {
            assertEquals(format, PixelFormat.values()[format.ordinal()], "ordinal must map back to the same constant");
        }
    }

    @Test
    void unknownNameIsRejected() {

        assertThrows(IllegalArgumentException.class, () -> PixelFormat.valueOf("NV12"));
    }
}