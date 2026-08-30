package com.jsoftsip.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallDurationFormatterTest {

    @Test
    void formatsZeroSeconds() {

        assertEquals("0:00", CallDurationFormatter.format(0));
    }

    @Test
    void padsSecondsBelowTen() {

        assertEquals("0:42", CallDurationFormatter.format(42));

        assertEquals("0:09", CallDurationFormatter.format(9));
    }

    @Test
    void formatsWholeMinutes() {

        assertEquals("1:42", CallDurationFormatter.format(102));
    }

    @Test
    void padsSecondsAtMinuteBoundary() {

        assertEquals("2:05", CallDurationFormatter.format(125));
    }

    @Test
    void rollsOverPastOneHour() {

        assertEquals("59:59", CallDurationFormatter.format(3599));

        assertEquals("60:00", CallDurationFormatter.format(3600));
    }
}
