package com.jsoftsip.core.registration;

import com.jsoftsip.core.account.SipAccount;

public interface RegistrationService extends AutoCloseable {

    void registerAccount(SipAccount account);

    void unregisterAccount(long accountId);

    /**
     * Re-provisions an account with a live registration using
     * its current SIP fields: the old UA is dropped and the
     * account is registered again with the new credentials.
     * Accounts without a live registration are ignored, they
     * pick up the new data on their next manual registration.
     */
    void reprovisionAccount(SipAccount account);

    /**
     * Accounts with a live registration right now. A baresip
     * restart wipes every account from the new process, so
     * session recovery re-registers exactly this list:
     * accounts the user deliberately unregistered (or whose
     * registration failed) are absent and stay offline.
     */
    java.util.List<SipAccount> getRegisteredAccounts();

    void addRegistrationListener(RegistrationListener listener);

    void removeRegistrationListener(RegistrationListener listener);

    /**
     * Releases any resources held by the service, such as scheduled
     * timeout executors. Implementations that hold no resources may
     * keep this as a no-op.
     */
    @Override
    default void close() {
        // no-op by default
    }
}