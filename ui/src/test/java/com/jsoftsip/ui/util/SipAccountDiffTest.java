package com.jsoftsip.ui.util;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SipAccountDiffTest {

    @Test
    void identicalSipFieldsHaveNoChanges() {

        SipAccount previous = account("user", "pass", "example.com", SipTransport.UDP);

        SipAccount updated = account("user", "pass", "example.com", SipTransport.UDP);

        assertFalse(SipAccountDiff.hasSipFieldChanges(previous, updated),
                    "an edit that keeps every SIP field unchanged must" + " not trigger a reprovision");
    }

    @Test
    void usernameChangeIsDetected() {

        assertTrue(changedOnly("newuser", "pass", "example.com", SipTransport.UDP),
                   "a username change must trigger a reprovision");
    }

    @Test
    void passwordChangeIsDetected() {

        assertTrue(changedOnly("user", "newpass", "example.com", SipTransport.UDP),
                   "a password change must trigger a reprovision");
    }

    @Test
    void domainChangeIsDetected() {

        assertTrue(changedOnly("user", "pass", "new.example.com", SipTransport.UDP),
                   "a domain change must trigger a reprovision");
    }

    @Test
    void transportChangeIsDetected() {

        assertTrue(changedOnly("user", "pass", "example.com", SipTransport.TLS),
                   "a transport change must trigger a reprovision");
    }

    @Test
    void snapshotCapturesTheSipFields() {

        SipAccount source = account("user", "pass", "example.com", SipTransport.TLS);

        source.setDisplayName("ignored");

        SipAccount snapshot = SipAccountDiff.snapshotSipFields(source);

        assertFalse(SipAccountDiff.hasSipFieldChanges(snapshot, source),
                    "the snapshot must equal the source SIP fields");
    }

    private static boolean changedOnly(String username, String password, String domain, SipTransport transport) {

        SipAccount previous = account("user", "pass", "example.com", SipTransport.UDP);

        SipAccount updated = account(username, password, domain, transport);

        return SipAccountDiff.hasSipFieldChanges(previous, updated);
    }

    private static SipAccount account(String username, String password, String domain, SipTransport transport) {

        SipAccount account = new SipAccount();

        account.setUsername(username);

        account.setPassword(password);

        account.setDomain(domain);

        account.setTransport(transport);

        return account;
    }
}