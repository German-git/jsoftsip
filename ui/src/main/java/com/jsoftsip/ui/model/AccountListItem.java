package com.jsoftsip.ui.model;

import com.jsoftsip.core.account.SipAccount;

public class AccountListItem {

    private final SipAccount account;

    public AccountListItem(SipAccount account) {
        this.account = account;
    }

    public SipAccount getAccount() {
        return account;
    }

    @Override
    public String toString() {

        return String.format("[%s] %s (%s@%s)", account.getStatus(), account.getDisplayName(), account.getUsername(),
                             account.getDomain());
    }
}