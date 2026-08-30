package com.jsoftsip.core.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoQualityTest {

    @Test
    void exposesWidthAndHeight() {

        VideoQuality quality = new VideoQuality(640, 480);

        assertEquals(640, quality.width());
        assertEquals(480, quality.height());
    }

    @Test
    void rejectsNonPositiveDimensions() {

        assertThrows(IllegalArgumentException.class, () -> new VideoQuality(0, 480));
        assertThrows(IllegalArgumentException.class, () -> new VideoQuality(640, 0));
        assertThrows(IllegalArgumentException.class, () -> new VideoQuality(-1, 480));
        assertThrows(IllegalArgumentException.class, () -> new VideoQuality(640, -1));
    }

    @Test
    void equalityIsBasedOnDimensions() {

        assertEquals(new VideoQuality(640, 480), new VideoQuality(640, 480));
        assertNotEquals(new VideoQuality(640, 480), new VideoQuality(320, 240));
        assertEquals(new VideoQuality(640, 480).hashCode(), new VideoQuality(640, 480).hashCode());
    }

    @Test
    void rendersAsWidthXHeight() {

        assertEquals("640x480", new VideoQuality(640, 480).toString());
    }
}