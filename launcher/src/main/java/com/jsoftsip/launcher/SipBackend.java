package com.jsoftsip.launcher;

import com.jsoftsip.core.logging.JSoftSipLog;

/**
 * SIP backend choice, resolved at launch time.
 *
 * <p>Selection is driven by the {@code jsoftsip.sip.backend} system
 * property ({@code mock} or {@code baresip}), the production default
 * is {@link #BARESIP}. A system property is used instead of the
 * {@code SettingsService} because the backend must be resolved before
 * any service exists: the mock backend changes how the composition
 * root itself is built, and the setting store is not available at
 * that point.
 */
public enum SipBackend {

    BARESIP,

    MOCK;

    public static final String SYSTEM_PROPERTY = "jsoftsip.sip.backend";

    public static SipBackend resolve() {

        String value = System.getProperty(SYSTEM_PROPERTY, BARESIP.name());

        SipBackend resolved;

        if (MOCK.name().equalsIgnoreCase(value.trim())) {

            resolved = MOCK;

        } else {

            resolved = BARESIP;
        }

        JSoftSipLog.info("SIP backend selected: " + resolved.name());

        return resolved;
    }
}
