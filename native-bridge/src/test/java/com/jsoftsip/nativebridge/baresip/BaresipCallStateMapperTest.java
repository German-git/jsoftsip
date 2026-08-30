package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Table-driven contract tests for the baresip-to-SipCallState
 * mapping: the mapper decides the whole call
 * flow, so every event type and the CALL_CLOSED failure
 * classification are pinned here, including the current substring
 * semantics that later work may refine.
 */
class BaresipCallStateMapperTest {

    private final BaresipCallStateMapper mapper = new BaresipCallStateMapper();

    @Test
    void nullEventTypeMapsToNull() {

        assertNull(mapper.map(null, "anything"), "a null event type must drop silently");
    }

    @ParameterizedTest
    @MethodSource("eventTable")
    void mapsEventsAccordingToTheContract(BaresipCallEventType type, String param, SipCallState expected) {

        assertEquals(expected, mapper.map(type, param));
    }

    static Stream<Arguments> eventTable() {

        return Stream.of(Arguments.of(BaresipCallEventType.CALL_OUTGOING, null, SipCallState.DIALING),
                         Arguments.of(BaresipCallEventType.CALL_OUTGOING, "ignored-param", SipCallState.DIALING),
                         Arguments.of(BaresipCallEventType.CALL_INCOMING, null, SipCallState.INCOMING),
                         Arguments.of(BaresipCallEventType.CALL_RINGING, null, SipCallState.RINGING),
                         Arguments.of(BaresipCallEventType.CALL_ESTABLISHED, null, SipCallState.ESTABLISHED),

                         // CALL_CLOSED classifies FAILED by matching the LEADING
                         // SIP status code, these rows pin
                         // that classification including its deliberate limits
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "404 Not Found", SipCallState.FAILED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "486 Busy Here", SipCallState.FAILED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "408 Request Timeout", SipCallState.FAILED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "480 Busy Here", SipCallState.FAILED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "503 Service Unavailable", SipCallState.FAILED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "Connection reset by peer",
                                      SipCallState.FAILED),

                         // A non-leading occurrence must not classify as failure
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "SIP/2.0 event 404 mentioned",
                                      SipCallState.TERMINATED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "4041 malformed code", SipCallState.TERMINATED),

                         // 487 is deliberate caller cancellation, not a failure:
                         // keeping TERMINATED preserves MISSED/CANCELLED semantics
                         // downstream for incoming legs cancelled by the caller
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "487 Request Terminated",
                                      SipCallState.TERMINATED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "normal call clearing",
                                      SipCallState.TERMINATED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, "", SipCallState.TERMINATED),
                         Arguments.of(BaresipCallEventType.CALL_CLOSED, null, SipCallState.TERMINATED),

                         // CALL_REMOTE_SDP is hold/answer only
                         Arguments.of(BaresipCallEventType.CALL_REMOTE_SDP, "hold", SipCallState.HOLD),
                         Arguments.of(BaresipCallEventType.CALL_REMOTE_SDP, "answer", SipCallState.ESTABLISHED),
                         Arguments.of(BaresipCallEventType.CALL_REMOTE_SDP, "inactive", null),
                         Arguments.of(BaresipCallEventType.CALL_REMOTE_SDP, null, null),

                         // Known-non-actionable events must keep dropping silently
                         Arguments.of(BaresipCallEventType.CALL_LOCAL_SDP, null, null),
                         Arguments.of(BaresipCallEventType.CALL_ANSWERED, null, null),
                         Arguments.of(BaresipCallEventType.CALL_RTPESTAB, null, null),
                         Arguments.of(BaresipCallEventType.CALL_RTCP, null, null),
                         Arguments.of(BaresipCallEventType.UA_CREATE, null, null),
                         Arguments.of(BaresipCallEventType.UA_SHUTDOWN, null, null),
                         Arguments.of(BaresipCallEventType.UNKNOWN, null, null));
    }
}
