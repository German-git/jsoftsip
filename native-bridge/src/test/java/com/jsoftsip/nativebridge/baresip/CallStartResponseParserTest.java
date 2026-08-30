package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser tests for the ctrl_tcp dial response:
 * the success shape plus every malformed payload variant must
 * degrade to a not-ok response instead of throwing, because this
 * parser runs on the backend reader thread.
 */
class CallStartResponseParserTest {

    private final CallStartResponseParser parser = new CallStartResponseParser();

    @Test
    void parsesSuccessfulDialWithCallIdAndToken() {

        CallStartResponse response = parser.parse("{\"ok\":true,\"data\":\"call id: abc-123 some trailing text\",\"token\":\"tok-9\"}");

        assertAll(() -> assertTrue(response.isOk(), "ok must surface as true"),
                  () -> assertEquals("abc-123", response.getCallId(), "the call id must be extracted from data"),
                  () -> assertEquals("tok-9", response.getToken(), "the token must be echoed back"));
    }

    @Test
    void extractsTheFirstCallIdWhenDataCarriesSeveral() {

        CallStartResponse response = parser.parse("{\"ok\":true,\"data\":\"call id: first then call id: second\"}");

        assertEquals("first", response.getCallId(), "only the first occurrence is taken");
    }

    @Test
    void matchesCallIdWithNoSpaceAfterTheColon() {

        CallStartResponse response = parser.parse("{\"ok\":true,\"data\":\"call id:tight-id\"}");

        assertEquals("tight-id", response.getCallId());
    }

    @Test
    void missingDataYieldsNullCallIdWithoutFailing() {

        CallStartResponse response = parser.parse("{\"ok\":true,\"token\":\"tok-1\"}");

        assertAll(() -> assertTrue(response.isOk()), () -> assertNull(response.getCallId(), "no data means no call id"),
                  () -> assertEquals("tok-1", response.getToken()));
    }

    @Test
    void missingTokenYieldsNullToken() {

        CallStartResponse response = parser.parse("{\"ok\":true,\"data\":\"call id: solo\"}");

        assertNull(response.getToken(), "an absent token must stay null");
    }

    /**
     * Characterization: the ok flag and the call id extraction are
     * independent today, the caller decides what a failed dial
     * with a stray call id means.
     */
    @Test
    void okFalseStillExtractsTheCallIdFromData() {

        CallStartResponse response = parser.parse("{\"ok\":false,\"data\":\"call id: x1\"}");

        assertAll(() -> assertFalse(response.isOk()), () -> assertEquals("x1", response.getCallId()));
    }

    @Test
    void malformedPayloadsDegradeToANotOkResponseWithoutThrowing() {

        for (String payload : new String[]{null, "", "not json at all", "{\"ok\":}", "[1,2]", "\"just a string\""}) {

            CallStartResponse response = parser.parse(payload);

            assertAll("payload: " + payload,
                      () -> assertFalse(response.isOk(), "a malformed payload must parse as not ok"),
                      () -> assertNull(response.getCallId(), "a malformed payload carries no call id"),
                      () -> assertNull(response.getToken(), "a malformed payload carries no token"));
        }
    }
}
