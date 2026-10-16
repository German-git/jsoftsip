package com.jsoftsip.ui.controller;

import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.ui.I18n;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryPaneControllerClipboardTest {

    @Test
    void copiedOriginAndDestinationRespectCallDirection() {

        CallHistoryEntry outgoing = entry(CallDirection.OUTGOING);
        assertEquals("1001", HistoryPaneController.originForCopy(outgoing));
        assertEquals("sip:alice@example.org", HistoryPaneController.destinationForCopy(outgoing));

        CallHistoryEntry incoming = entry(CallDirection.INCOMING);
        assertEquals("sip:alice@example.org", HistoryPaneController.originForCopy(incoming));
        assertEquals("1001", HistoryPaneController.destinationForCopy(incoming));
    }

    @Test
    void copiedRowMatchesTheVisibleHistoryFieldsAndFormatting() {

        CallHistoryEntry entry = entry(CallDirection.OUTGOING);
        entry.setStartedAt(LocalDateTime.of(2025, 4, 3, 13, 14, 15));
        entry.setDurationSeconds(125);
        entry.setResult(CallResult.ANSWERED);

        String expected = String.join(System.lineSeparator(), "13:14:15", "1001 ⇨ sip:alice@example.org",
                                      "2:05 " + I18n.get("call.result.answered"));

        assertEquals(expected, HistoryPaneController.formatRowData(entry));
    }

    private static CallHistoryEntry entry(CallDirection direction) {

        CallHistoryEntry entry = new CallHistoryEntry();
        entry.setAccountUsername("1001");
        entry.setDestination("sip:alice@example.org");
        entry.setDirection(direction);
        return entry;
    }
}
