package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountInputValidatorTest {

    @Test
    void emptyAccountIsInvalid() {

        SipAccount account = new SipAccount();

        assertFalse(AccountInputValidator.isValid(account), "a blank account must be rejected");
    }

    @Test
    void blankUsernameOrDomainIsInvalid() {

        SipAccount account = new SipAccount();

        account.setUsername("alice");
        account.setDomain("   ");

        assertFalse(AccountInputValidator.isValid(account), "blank domain must be rejected");

        account.setUsername("   ");
        account.setDomain("example.com");

        assertFalse(AccountInputValidator.isValid(account), "blank username must be rejected");
    }

    @Test
    void validAccountIsAccepted() {

        SipAccount account = new SipAccount();

        account.setUsername("alice");
        account.setDomain("example.com");
        account.setTransport(SipTransport.UDP);

        assertTrue(AccountInputValidator.isValid(account), "non-empty username and domain must be accepted");
    }

    @Test
    void optionalFieldsDoNotAffectValidity() {

        SipAccount account = new SipAccount();

        account.setUsername("bob");
        account.setDomain("example.com");
        account.setDisplayName("");
        account.setPassword("");

        assertTrue(AccountInputValidator.isValid(account),
                   "empty display name and password must not invalidate the account");
    }
}
