package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallDirectionPresentationTest {

    private static final Map<CallDirection, CallDirectionPresentation> EXPECTED = new EnumMap<>(
        Map.of(CallDirection.INCOMING, new CallDirectionPresentation("\u21E6", "direction-incoming"),
               CallDirection.OUTGOING, new CallDirectionPresentation("\u21E8", "direction-outgoing")));

    @Test
    void mapsEveryDirectionToGlyphAndCssClass() {

        for (CallDirection direction : CallDirection.values()) {

            assertEquals(EXPECTED.get(direction), CallDirectionPresentation.forDirection(direction), direction.name());
        }
    }

    @Test
    void incomingUsesLeftwardsWhiteArrowGlyph() {

        assertEquals("\u21E6", CallDirectionPresentation.forDirection(CallDirection.INCOMING).glyph());
    }

    @Test
    void outgoingUsesRightwardsWhiteArrowGlyph() {

        assertEquals("\u21E8", CallDirectionPresentation.forDirection(CallDirection.OUTGOING).glyph());
    }

    @Test
    void incomingUsesIncomingCssClass() {

        assertEquals("direction-incoming", CallDirectionPresentation.forDirection(CallDirection.INCOMING).cssClass());
    }

    @Test
    void outgoingUsesOutgoingCssClass() {

        assertEquals("direction-outgoing", CallDirectionPresentation.forDirection(CallDirection.OUTGOING).cssClass());
    }
}