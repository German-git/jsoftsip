package com.jsoftsip.ui.util;

import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.ui.I18n;

/**
 * JavaFX-free mapping from a failed registration event to the
 * text shown in the registration failure alert. Kept free of
 * JavaFX imports so plain unit tests reach it headless.
 *
 * The account identity comes from the event message, which the
 * backend fills with the normalized AOR (sip:user@domain), so
 * no service lookup is needed: user@domain is exactly the
 * username/domain pair used to provision the account.
 */
public record RegistrationFailurePresentation(String message) {

    public static RegistrationFailurePresentation forEvent(SipRegistrationEvent event) {

        String identity = accountIdentity(event);

        String detail = failureDetail(event);

        if (detail == null) {

            return new RegistrationFailurePresentation(I18n.format("registration.failed.unknown", identity));
        }

        return new RegistrationFailurePresentation(I18n.format("registration.failed.withDetail", identity, detail));
    }

    private static String accountIdentity(SipRegistrationEvent event) {

        String aor = event.getMessage();

        if (aor == null || aor.isBlank()) {
            return "unknown account";
        }

        if (aor.startsWith("sip:")) {
            return aor.substring(4);
        }

        return aor;
    }

    private static String failureDetail(SipRegistrationEvent event) {

        Integer code = event.getCode();

        String reason = event.getReason();

        if (code != null && reason != null) {
            return code + " " + reason;
        }

        if (code != null) {
            return String.valueOf(code);
        }

        return reason;
    }
}