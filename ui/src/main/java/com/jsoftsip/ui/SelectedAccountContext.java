package com.jsoftsip.ui;

import com.jsoftsip.core.account.SipAccount;

/**
 * Holds the currently selected account in the UI.
 * Per-instance state, owned by the {@link AppContext}.
 */
public class SelectedAccountContext {

    private SipAccount selectedAccount;

    public SipAccount getSelectedAccount() {
        return selectedAccount;
    }

    public void setSelectedAccount(SipAccount account) {
        selectedAccount = account;
    }
}
