package com.jsoftsip.core.registration;

import com.jsoftsip.core.sip.SipRegistrationEvent;

/**
 * Receives every registration event re-dispatched by the
 * RegistrationService, so consumers outside the registration
 * pipeline (e.g. the UI) can react to failures without coupling
 * to the SIP transport listeners.
 */
public interface RegistrationListener {

    void onRegistrationEvent(SipRegistrationEvent event);
}