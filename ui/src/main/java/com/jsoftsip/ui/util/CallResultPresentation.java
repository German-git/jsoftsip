package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.ui.I18n;

/**
 * JavaFX-free mapping from CallResult to the text and CSS class
 * shown in the call history rows. Kept free of JavaFX imports so
 * plain unit tests reach it headless.
 */
public record CallResultPresentation(String displayText, String cssClass) {

    public static CallResultPresentation forResult(CallResult result) {

        return switch (result) {

            case ANSWERED -> new CallResultPresentation(I18n.get("call.result.answered"), "result-answered");

            case REJECTED -> new CallResultPresentation(I18n.get("call.result.rejected"), "result-rejected");

            case MISSED -> new CallResultPresentation(I18n.get("call.result.missed"), "result-missed");

            case FAILED -> new CallResultPresentation(I18n.get("call.result.failed"), "result-failed");

            case CANCELLED -> new CallResultPresentation(I18n.get("call.result.cancelled"), "result-cancelled");
        };
    }
}
