package com.jsoftsip.ui.util;

import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipRegistrationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistrationFailurePresentationTest {

    @Test
    void formatsCodeAndReason() {

        assertEquals("Registration failed for alice@example.com:" + " 403 Forbidden",
                     RegistrationFailurePresentation.forEvent(event(403, "Forbidden")).message());
    }

    @Test
    void formatsReasonOnly() {

        assertEquals("Registration failed for alice@example.com:" + " Unauthorized",
                     RegistrationFailurePresentation.forEvent(event(null, "Unauthorized")).message());
    }

    @Test
    void formatsCodeOnly() {

        assertEquals("Registration failed for alice@example.com:" + " 401",
                     RegistrationFailurePresentation.forEvent(event(401, null)).message());
    }

    @Test
    void fallsBackToGenericMessageWithoutDetails() {

        assertEquals("Registration failed for alice@example.com." + " Check your credentials and try again.",
                     RegistrationFailurePresentation.forEvent(event(null, null)).message());
    }

    @Test
    void derivesIdentityFromTheEventAor() {

        assertEquals("Registration failed for alice@example.com:" + " 403 Forbidden",
                     RegistrationFailurePresentation.forEvent(new SipRegistrationEvent(1, SipRegistrationState.FAILED,
                         "sip:alice@example.com", 403, "Forbidden")).message());
    }

    private static SipRegistrationEvent event(Integer code, String reason) {

        return new SipRegistrationEvent(1, SipRegistrationState.FAILED, "sip:alice@example.com", code, reason);
    }
}