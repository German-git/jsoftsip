package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipPeer;

import java.time.LocalDateTime;

public class DefaultCallService extends AbstractCallService implements SipCallListener {

    public DefaultCallService(SipClient sipClient, AccountService accountService, HistoryService historyService) {

        super(sipClient, accountService, historyService);

        sipClient.addCallListener(this);
    }

    @Override
    public CallLeg startCall(SipAccount account, String destination) {

        String backendCallId = sipClient.startCall(account.getId(), destination);

        return registerOutgoingCall(account, destination, backendCallId);
    }

    @Override
    public void holdCall(String callId) {

        // Pure backend command passthrough: no optimistic local
        // transition and no listener notification from this
        // method. Product decision: the
        // UI offers Hold only for app-to-external calls (it stays
        // disabled for intra-app ones), and the old optimistic
        // transition stranded legs in eternal HOLD when baresip
        // dropped the action without a matching event. The leg
        // state changes exclusively through onCallEvent, where
        // the SDP answer of the hold re-INVITE confirms it, and
        // remote holds land through the same rule
        //
        // The session monitor wrapping stays so the command cannot
        // interleave with a concurrent teardown of the same session
        findCall(callId).ifPresent(call -> {

            synchronized (stateMonitorOf(call)) {

                sipClient.holdCall(call.getBackendCallId());
            }
        });
    }

    @Override
    public void resumeCall(String callId) {

        // Same passthrough shape as holdCall, which also removes
        // the historical send-before-transition asymmetry of this
        // method
        findCall(callId).ifPresent(call -> {

            synchronized (stateMonitorOf(call)) {

                sipClient.resumeCall(call.getBackendCallId());
            }
        });
    }

    @Override
    public void onCallEvent(CallEvent event) {

        JSoftSipLog.trace("CallLeg event: " + event.getState() + " - " + event.getCallId());

        processCallEvent(event);
    }

    /**
     * True when an incoming INVITE is a forked duplicate of a call that
     * already belongs to a session. Baresip can report a single INVITE
     * as several INCOMING events with distinct call ids (for example
     * when the account is registered on multiple network interfaces and
     * the INVITE is forked to each contact). The correlation key reuses
     * {@link CallSession#sessionKey}, so every forked event maps to the
     * session that already owns the incoming leg and collapses to one
     * card instead of minting a duplicate.
     */
    @Override
    protected boolean isForkedIncomingCall(CallEvent event) {

        SipAccount account = accountService.findById(event.getAccountId()).orElse(null);

        if (account == null) {
            return false;
        }

        String key = CallSession.sessionKey(account, event.getRemoteUri());

        if (key == null) {
            return false;
        }

        CallSession session = sessionsByKey.get(key);

        if (session == null) {
            return false;
        }

        return session.getLegs().stream()
                      .anyMatch(leg -> leg.getDirection() == CallDirection.INCOMING && leg.getState() != CallState.ENDED
                          && leg.getAccount() != null && leg.getAccount().getId() == event.getAccountId());
    }

    @Override
    protected void applyCallEvent(CallLeg call, CallEvent event, CallState next) {

        switch (event.getState()) {

            case ESTABLISHED -> {

                call.setResult(CallResult.ANSWERED);

                // Refine the peer-local flag with the real peer
                // URI now that the call is established. The dial
                // target may be a bare number, whose host cannot
                // be known until the backend reports the peer.
                call.setPeerLocalAccount(SipPeer.isLocalAccount(accountService.getAccounts(), event.getRemoteUri()));

                if (call.getStartedAt() == null) {

                    call.setStartedAt(LocalDateTime.now());
                }
            }

            case FAILED -> {

                if (call.getResult() == null) {

                    call.setResult(CallResult.FAILED);
                }
            }

            default -> {
            }
        }
    }

    @Override
    protected void onIncomingCallStarted(CallEvent event) {

        JSoftSipLog.info("Creating incoming call");
    }

    @Override
    protected void onIncomingCallCreated(CallLeg call, CallEvent event) {

        call.setPeerLocalAccount(SipPeer.isLocalAccount(accountService.getAccounts(), event.getRemoteUri()));
    }

    @Override
    protected void onFinishingCall(CallLeg call, String backendCallId) {

        JSoftSipLog.info("Finishing call " + backendCallId);
    }

    @Override
    protected void onCallFinished(CallLeg call) {

        JSoftSipLog.info("Active calls: " + activeCalls.size());
    }
}
