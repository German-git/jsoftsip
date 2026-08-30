package com.jsoftsip.ui.util;

import com.jsoftsip.core.account.SipAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountIdentityFormatterTest {

    @Test
    void formatInlineUsesDisplayNameAndAor() {

        SipAccount account = new SipAccount();

        account.setDisplayName("Alice");
        account.setUsername("alice");
        account.setDomain("example.com");

        assertEquals("Alice (alice@example.com)", AccountIdentityFormatter.formatInline(account));
    }

    @Test
    void formatInlineFallsBackToAorWhenDisplayNameIsBlank() {

        SipAccount account = new SipAccount();

        account.setUsername("bob");
        account.setDomain("example.com");

        assertEquals("bob@example.com", AccountIdentityFormatter.formatInline(account));
    }

    @Test
    void formatInlineHandlesNullAccount() {

        assertEquals("Unknown account", AccountIdentityFormatter.formatInline(null));
    }

    @Test
    void formatInlineHandlesMissingUsernameOrDomain() {

        SipAccount account = new SipAccount();

        account.setDisplayName("Charlie");
        account.setDomain("example.com");

        assertEquals("Charlie (example.com)", AccountIdentityFormatter.formatInline(account));

        account.setDomain(null);
        account.setUsername("charlie");

        assertEquals("Charlie (charlie)", AccountIdentityFormatter.formatInline(account));
    }

    @Test
    void formatProminentUsesDisplayName() {

        SipAccount account = new SipAccount();

        account.setDisplayName("Alice");
        account.setUsername("alice");
        account.setDomain("example.com");

        assertEquals("Alice", AccountIdentityFormatter.formatProminent(account));
    }

    @Test
    void formatProminentFallsBackToSipAor() {

        SipAccount account = new SipAccount();

        account.setUsername("bob");
        account.setDomain("example.com");

        assertEquals("sip:bob@example.com", AccountIdentityFormatter.formatProminent(account));
    }

    @Test
    void formatProminentHandlesNullAccount() {

        assertEquals("", AccountIdentityFormatter.formatProminent(null));
    }

    @Test
    void formatProminentHandlesMissingUsernameOrDomain() {

        SipAccount account = new SipAccount();

        account.setUsername("charlie");

        assertEquals("sip:charlie", AccountIdentityFormatter.formatProminent(account));

        account.setUsername(null);
        account.setDomain("example.com");

        assertEquals("sip:@example.com", AccountIdentityFormatter.formatProminent(account));
    }
}
