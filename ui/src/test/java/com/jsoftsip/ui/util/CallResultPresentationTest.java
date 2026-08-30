package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallResult;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallResultPresentationTest {

    private static final Map<CallResult, CallResultPresentation> EXPECTED = new EnumMap<>(
        Map.of(CallResult.ANSWERED, new CallResultPresentation("Answered", "result-answered"), CallResult.REJECTED,
               new CallResultPresentation("Rejected", "result-rejected"), CallResult.MISSED,
               new CallResultPresentation("Missed", "result-missed"), CallResult.FAILED,
               new CallResultPresentation("Failed", "result-failed"), CallResult.CANCELLED,
               new CallResultPresentation("Cancelled", "result-cancelled")));

    @Test
    void mapsEveryResultToDisplayTextAndCssClass() {

        for (CallResult result : CallResult.values()) {

            assertEquals(EXPECTED.get(result), CallResultPresentation.forResult(result), result.name());
        }
    }

    @Test
    void displayTextIsCapitalizedEnumName() {

        for (CallResult result : CallResult.values()) {

            String name = result.name();

            String expected = name.charAt(0) + name.substring(1).toLowerCase();

            assertEquals(expected, CallResultPresentation.forResult(result).displayText(), result.name());
        }
    }
}
