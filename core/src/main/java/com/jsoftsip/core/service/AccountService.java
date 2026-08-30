package com.jsoftsip.core.service;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;

import java.util.List;

public interface AccountService {

    SipAccount createAccount(SipAccount account);

    SipAccount updateAccount(SipAccount account);

    void deleteAccount(long id);

    List<SipAccount> getAccounts();

    void updateStatus(long accountId, AccountStatus status);

    void addListener(AccountStatusListener listener);

    void removeListener(AccountStatusListener listener);

    java.util.Optional<SipAccount> findById(long id);

    /**
     * Rotates the master encryption key and re-encrypts every stored SIP
     * account password under the new key. On failure the previous master key
     * is restored.
     */
    void rotateMasterKey();
}