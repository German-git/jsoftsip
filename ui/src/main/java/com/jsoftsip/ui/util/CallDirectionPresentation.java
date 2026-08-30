package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallDirection;

/**
 * JavaFX-free mapping from CallDirection to the glyph and CSS class
 * shown in the active call cards. Kept free of JavaFX imports so
 * plain unit tests reach it headless.
 */
public record CallDirectionPresentation(String glyph, String cssClass) {

    public static CallDirectionPresentation forDirection(CallDirection direction) {

        return switch (direction) {

            case INCOMING -> new CallDirectionPresentation("\u21E6", "direction-incoming");

            case OUTGOING -> new CallDirectionPresentation("\u21E8", "direction-outgoing");
        };
    }
}