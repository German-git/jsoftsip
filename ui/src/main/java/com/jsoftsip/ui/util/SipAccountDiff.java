package com.jsoftsip.ui.util;

import com.jsoftsip.core.account.SipAccount;

import java.util.Objects;

/**
 * JavaFX-free comparison of the SIP provisioning fields of two
 * accounts. Kept free of JavaFX imports so plain unit tests
 * reach it headless.
 *
 * The edit dialog mutates the pre-edit account instance in
 * place, so callers must capture the previous values with
 * snapshotSipFields before the dialog opens.
 */
public final class SipAccountDiff {

    private SipAccountDiff() {
    }

    public static SipAccount snapshotSipFields(SipAccount account) {

        SipAccount snapshot = new SipAccount();

        snapshot.setUsername(account.getUsername());

        snapshot.setPassword(account.getPassword());

        snapshot.setDomain(account.getDomain());

        snapshot.setTransport(account.getTransport());

        return snapshot;
    }

    public static boolean hasSipFieldChanges(SipAccount previous, SipAccount updated) {

        return changed(previous.getUsername(), updated.getUsername())
            || changed(previous.getPassword(), updated.getPassword())
            || changed(previous.getDomain(), updated.getDomain()) || previous.getTransport() != updated.getTransport();
    }

    private static boolean changed(String previous, String updated) {

        return !Objects.equals(previous, updated);
    }
}