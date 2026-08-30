package com.jsoftsip.ui.window;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowGeometryTest {

    @Test
    void serializeProducesCommaSeparatedXyWidthHeight() {

        WindowGeometry geometry = new WindowGeometry(10.0, 20.0, 800.0, 640.0);

        assertEquals("10.0,20.0,800.0,640.0", geometry.serialize());
    }

    @Test
    void parseRoundTripsASerializedGeometry() {

        WindowGeometry original = new WindowGeometry(15.5, 30.25, 1024.0, 768.0);

        Optional<WindowGeometry> parsed = WindowGeometry.parse(original.serialize());

        assertTrue(parsed.isPresent());
        assertEquals(original, parsed.get());
    }

    @Test
    void parseRejectsNullAndBlank() {

        assertTrue(WindowGeometry.parse(null).isEmpty());
        assertTrue(WindowGeometry.parse("").isEmpty());
        assertTrue(WindowGeometry.parse("   ").isEmpty());
    }

    @Test
    void parseRejectsWrongPartCount() {

        assertTrue(WindowGeometry.parse("10,20,800").isEmpty());

        assertTrue(WindowGeometry.parse("10,20,800,600,extra").isEmpty());
    }

    @Test
    void parseRejectsNonNumericParts() {

        assertTrue(WindowGeometry.parse("10,20,wide,600").isEmpty());
    }

    @Test
    void parseRejectsNonPositiveWidthAndHeight() {

        assertTrue(WindowGeometry.parse("10,20,0,600").isEmpty());

        assertTrue(WindowGeometry.parse("10,20,800,-1").isEmpty());
    }

    @Test
    void parseRejectsNonFiniteValues() {

        assertTrue(WindowGeometry.parse("NaN,20,800,600").isEmpty());

        assertTrue(WindowGeometry.parse("10,Infinity,800,600").isEmpty());
    }

    @Test
    void parseAcceptsNegativePositionForMultiMonitor() {

        Optional<WindowGeometry> parsed = WindowGeometry.parse("-1920.0,0.0,800.0,600.0");

        assertTrue(parsed.isPresent());
        assertEquals(-1920.0, parsed.get().x());
        assertEquals(0.0, parsed.get().y());
    }

    @Test
    void parseToleratesWhitespaceAroundParts() {

        Optional<WindowGeometry> parsed = WindowGeometry.parse(" 10 , 20 , 800 , 600 ");

        assertTrue(parsed.isPresent());
        assertEquals(new WindowGeometry(10.0, 20.0, 800.0, 600.0), parsed.get());
    }
}
