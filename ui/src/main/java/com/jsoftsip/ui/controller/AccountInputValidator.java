package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;

/**
 * Validates the minimum fields required to persist a SIP account.
 * Display name and password are optional, username and domain are not.
 */
final class AccountInputValidator {

    private AccountInputValidator() {
    }

    static boolean isValid(SipAccount account) {

        return account != null && !isBlank(account.getUsername()) && !isBlank(account.getDomain());
    }

    private static boolean isBlank(String value) {

        return value == null || value.isBlank();
    }
}
