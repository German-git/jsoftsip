package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.registration.RegistrationService;

import java.util.List;

/**
 * Full SIP session restart behind the settings apply flow.
 * A plain process restart leaves the app broken: the
 * ctrl_tcp socket dies with the old process and never
 * reconnects, and the new process starts with zero accounts
 * because the launcher deletes the accounts file on every
 * launch by design (provisioning happens via ctrl_tcp only).
 *
 * <p>This operation therefore restarts the whole session:
 * process restart, then cleanup of the call state that died
 * with the old process, then ctrl_tcp reconnect, then
 * re-provisioning (uanew) of exactly the accounts that had
 * a live registration before the restart. Accounts the user
 * deliberately unregistered are not resurrected. The ctrl_tcp
 * socket is disconnected before the process is killed so the
 * reader death of the old process is the expected outcome of
 * the intentional restart, never a reported failure.
 *
 * <p>Any failing step reports false, which routes the apply
 * flow into its rollback path, the rollback restart runs
 * this same composite, so the session also recovers on the
 * restored config. Process ownership stays in
 * {@link BaresipLauncher}.
 */
public class BaresipSessionRestart implements BaresipConfigService.RestartOperation {

    private final BaresipLauncher launcher;

    private final BaresipSipClient sipClient;

    private final RegistrationService registrationService;

    public BaresipSessionRestart(BaresipLauncher launcher, BaresipSipClient sipClient,
                                 RegistrationService registrationService) {

        this.launcher = launcher;
        this.sipClient = sipClient;
        this.registrationService = registrationService;

        // Wire the launcher with the ctrl_tcp owner: the
        // composition root builds them separately, and the
        // ordered shutdown must disconnect the socket before
        // the process is killed
        launcher.attachSipClient(sipClient);
    }

    @Override
    public synchronized boolean restart() {

        // Serialized: the settings-apply flow and the crash
        // supervisor share this one instance, so a user apply
        // racing an automatic recovery must never interleave
        // two stop/start cycles on the same process

        // Snapshot first: the registration tracking survives
        // the process restart, but capturing the list up
        // front keeps the operation independent of event
        // timing while the socket is down
        List<SipAccount> accountsToRegister = registrationService.getRegisteredAccounts();

        // Ordered teardown before the process restart: killing
        // the process closes the socket under the reader, and
        // that reader death is the expected outcome of the
        // intentional restart, not a failure to report. The
        // reconnect below opens a fresh socket, whose connect
        // resets the intentional-close flag
        sipClient.shutdown();

        boolean processUp = launcher.restart();

        // Every tracked call died with the old process even
        // when the new one fails to start, so the stale call
        // state is dropped unconditionally
        sipClient.terminateSessionCalls();

        if (!processUp) {
            return false;
        }

        if (!sipClient.reconnect()) {

            log("Restart failed: ctrl_tcp reconnect refused");

            return false;
        }

        try {

            accountsToRegister.forEach(registrationService::registerAccount);

        } catch (RuntimeException exception) {

            BaresipLog.error("Restart failed: account re-provisioning", exception);

            return false;
        }

        log("Session restarted, " + accountsToRegister.size() + " account(s) re-provisioned");

        return true;
    }

    private static void log(String message) {

        BaresipLog.info(message);
    }
}
