package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.nativebridge.baresip.event.BaresipCallEvent;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventParser;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaresipCallEventParserTest {

    private final BaresipCallEventParser parser = new BaresipCallEventParser();

    @Test
    void parsesKnownNonActionableCallEventTypes() {

        BaresipCallEvent answered = parser.parse(callJson("CALL_ANSWERED"));

        assertNotNull(answered, "CALL_ANSWERED must parse as a call event");

        assertEquals(BaresipCallEventType.CALL_ANSWERED, answered.getType(),
                     "CALL_ANSWERED must map through valueOf," + " not UNKNOWN");

        BaresipCallEvent rtpEstablished = parser.parse(callJson("CALL_RTPESTAB"));

        assertNotNull(rtpEstablished, "CALL_RTPESTAB must parse as a call event");

        assertEquals(BaresipCallEventType.CALL_RTPESTAB, rtpEstablished.getType(),
                     "CALL_RTPESTAB must map through valueOf," + " not UNKNOWN");

        BaresipCallEvent rtcpStats = parser.parse(callJson("CALL_RTCP"));

        assertNotNull(rtcpStats, "CALL_RTCP must parse as a call event");

        assertEquals(BaresipCallEventType.CALL_RTCP, rtcpStats.getType(),
                     "CALL_RTCP must map through valueOf," + " not UNKNOWN");
    }

    @Test
    void unmappedWireTypeStillMapsToUnknown() {

        BaresipCallEvent event = parser.parse(callJson("BOGUS_CALL"));

        assertNotNull(event, "an unmapped type must still parse as an event");

        assertEquals(BaresipCallEventType.UNKNOWN, event.getType(), "an unmapped wire type must stay UNKNOWN");
    }

    @Test
    void knownNonActionableConstantsExist() {

        assertEquals(BaresipCallEventType.CALL_ANSWERED, BaresipCallEventType.valueOf("CALL_ANSWERED"),
                     "CALL_ANSWERED must be a declared constant");

        assertEquals(BaresipCallEventType.CALL_RTPESTAB, BaresipCallEventType.valueOf("CALL_RTPESTAB"),
                     "CALL_RTPESTAB must be a declared constant");

        assertEquals(BaresipCallEventType.CALL_RTCP, BaresipCallEventType.valueOf("CALL_RTCP"),
                     "CALL_RTCP must be a declared constant");

        assertEquals(BaresipCallEventType.UA_CREATE, BaresipCallEventType.valueOf("UA_CREATE"),
                     "UA_CREATE must be a declared constant");

        assertEquals(BaresipCallEventType.UA_SHUTDOWN, BaresipCallEventType.valueOf("UA_SHUTDOWN"),
                     "UA_SHUTDOWN must be a declared constant");
    }

    private static String callJson(String type) {

        return "{\"event\":true,\"class\":\"call\",\"type\":\"" + type + "\",\"id\":\"call-1\",\"accountaor\":"
            + "\"sip:alice@example.com\",\"peeruri\":" + "\"sip:bob@example.com\",\"direction\":"
            + "\"incoming\",\"param\":\"\"}";
    }
}