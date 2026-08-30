package com.jsoftsip.ui.util;

import com.jsoftsip.core.account.SipAccount;

/**
 * Single, null-safe formatter for account identity strings used in the UI.
 * Centralizes the three ad-hoc formats that previously existed in
 * DialerDialogController, AccountsPaneController and VideoCallDialogController.
 */
public final class AccountIdentityFormatter {

    private AccountIdentityFormatter() {
    }

    /**
     * Returns a label like {@code "Display Name (user@domain)"}. When the
     * display name is missing it falls back to {@code "user@domain"}. When
     * the account is null it returns {@code "Unknown account"}.
     */
    public static String formatInline(SipAccount account) {

        if (account == null) {
            return "Unknown account";
        }

        String displayName = nonBlank(account.getDisplayName());
        String username = nonBlank(account.getUsername());
        String domain = nonBlank(account.getDomain());

        String aor = aor(username, domain);

        if (displayName != null && aor != null) {
            return displayName + " (" + aor + ")";
        }

        if (displayName != null) {
            return displayName;
        }

        if (aor != null) {
            return aor;
        }

        return "Unknown account";
    }

    /**
     * Returns the most prominent identity: the display name when available,
     * otherwise a {@code sip:user@domain} AOR. Returns an empty string when
     * the account is null, matching the previous VideoCallDialog behavior.
     */
    public static String formatProminent(SipAccount account) {

        if (account == null) {
            return "";
        }

        String displayName = nonBlank(account.getDisplayName());

        if (displayName != null) {
            return displayName;
        }

        String username = account.getUsername();
        String domain = account.getDomain();

        if (username == null && domain == null) {
            return "";
        }

        StringBuilder aor = new StringBuilder("sip:");

        if (username != null) {
            aor.append(username);
        }

        if (domain != null) {
            aor.append('@').append(domain);
        }

        return aor.toString();
    }

    private static String aor(String username, String domain) {

        if (username == null && domain == null) {
            return null;
        }

        if (domain == null) {
            return username;
        }

        if (username == null) {
            return domain;
        }

        return username + "@" + domain;
    }

    private static String nonBlank(String value) {

        return value == null || value.isBlank() ? null : value;
    }
}
