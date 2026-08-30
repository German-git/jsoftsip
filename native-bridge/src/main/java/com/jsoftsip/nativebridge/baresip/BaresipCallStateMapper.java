package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventType;
import com.jsoftsip.core.sip.SipCallState;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaresipCallStateMapper {

    /**
     * Matches the SIP status code only when it LEADS the close
     * reason, so a code merely mentioned inside the text can no
     * longer classify the call as failed.
     */
    private static final Pattern LEADING_SIP_STATUS = Pattern.compile("^\\s*(\\d{3})\\b");

    /**
     * Close codes treated as real failures. 480 and 503 joined the
     * original trio because busy and service-unavailable dials are
     * failures for the history view, 487 is deliberately absent
     * because caller cancellation is not a failure and must keep
     * its MISSED/CANCELLED semantics downstream.
     */
    private static final Set<String> FAILURE_STATUS_CODES = Set.of("404", "408", "480", "486", "503");

    public SipCallState map(BaresipCallEventType type, String param) {

        if (type == null) {
            return null;
        }

        return switch (type) {

            case CALL_OUTGOING -> SipCallState.DIALING;

            case CALL_INCOMING -> SipCallState.INCOMING;

            case CALL_RINGING -> SipCallState.RINGING;

            case CALL_ESTABLISHED -> SipCallState.ESTABLISHED;

            case CALL_CLOSED -> {

                if (isFailureClose(param)) {
                    yield SipCallState.FAILED;
                }

                yield SipCallState.TERMINATED;
            }

            case CALL_REMOTE_SDP -> {

                if ("hold".equals(param)) {
                    yield SipCallState.HOLD;
                }

                if ("answer".equals(param)) {
                    yield SipCallState.ESTABLISHED;
                }

                yield null;
            }

            /*
             * Does not change the call state.
             * It only indicates that a local SDP was generated.
             */
            // The five baresip 4.6.0 known-non-actionable
            // events also map to null on purpose: the call
            // flow works without them and they must drop
            // silently, never with the UNKNOWN warn.
            case CALL_LOCAL_SDP, CALL_ANSWERED, CALL_RTPESTAB, CALL_RTCP, UA_CREATE, UA_SHUTDOWN, UNKNOWN -> null;
        };
    }

    /**
     * True when a CALL_CLOSED reason denotes a real failure: a
     * leading SIP error code from {@link #FAILURE_STATUS_CODES},
     * or a transport-level connection reset that carries no
     * status code at all.
     */
    private static boolean isFailureClose(String param) {

        if (param == null) {
            return false;
        }

        Matcher leadingCode = LEADING_SIP_STATUS.matcher(param);

        if (leadingCode.find()) {
            return FAILURE_STATUS_CODES.contains(leadingCode.group(1));
        }

        return param.contains("Connection reset");
    }
}