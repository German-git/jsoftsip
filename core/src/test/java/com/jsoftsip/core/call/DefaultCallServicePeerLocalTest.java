package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipEventListener;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the peer-local detection on the call service: a call
 * whose peer is another account of this same application must
 * be flagged (peerLocalAccount) at creation and refined when
 * the backend reports the real peer URI on establishment.
 * Local accounts register with prefixed AOR usernames
 * ("2_1003"), while the peer URI carries the bare number
 * ("sip:1003@host"), so the comparison must strip the
 * instance prefixes from both sides.
 */
class DefaultCallServicePeerLocalTest {

    private static final String CALL_ID = "call-1";

    private static final String PEER_DOMAIN = "192.168.0.97";

    private static SipAccount account(long id, String username, String domain) {
        SipAccount account = new SipAccount();
        account.setId(id);
        account.setUsername(username);
        account.setPassword("secret");
        account.setDomain(domain);
        return account;
    }

    @Test
    void outgoingCallToLocalAccountNumberIsPeerLocal() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(callee);

        DefaultCallService service = new DefaultCallService(new RecordingSipClient(), accounts,
            new NoOpHistoryService());

        CallLeg call = service.startCall(caller, "1002");

        assertTrue(call.isPeerLocalAccount(),
                   "dialing the number of a local account must" + " flag the call as peer-local");
    }

    @Test
    void forkedIncomingCallFromSamePeerCollapsesToOneCard() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount callee = account(1L, "2_1003", PEER_DOMAIN);

        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // Baresip can fork a single INVITE to several contacts,
        // each reported as a separate INCOMING event with its own
        // call id. They are the same logical call and must not
        // produce several active call cards.
        String peer = "sip:2_1002@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("fork-a", 1L, peer, SipCallState.INCOMING));
        sipClient.injectEvent(new CallEvent("fork-b", 1L, peer, SipCallState.INCOMING));
        sipClient.injectEvent(new CallEvent("fork-c", 1L, peer, SipCallState.INCOMING));

        List<CallLeg> active = service.getActiveCalls();

        assertEquals(1, active.size(), "forked duplicates of the same incoming call must collapse to one card");
        assertEquals(CallDirection.INCOMING, active.get(0).getDirection());

        // CSM-001: the collapsed card must be owned by a single session that
        // did not mint an extra leg for each forked INVITE.
        CallLeg collapsable = (CallLeg) active.get(0);
        CallSession session = collapsable.getSession();
        assertNotNull(session, "the collapsed call must belong to a CallSession");
        assertEquals(1, session.getLegs().size(), "forked duplicates must not add extra legs to the session");
        assertTrue(session.isActive(), "the session must be active while the single leg is ringing");
    }

    @Test
    void rejectingIncomingLegOfLocalCallAlsoEndsOutgoingLeg() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);
        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // Local call: 1003 dials 1002. The same logical call is
        // shown as two cards (outgoing on 1003, incoming on 1002).
        CallLeg outgoing = (CallLeg) service.startCall(caller, "1002");

        String peer = "sip:2_1003@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-1", 2L, peer, SipCallState.INCOMING));

        List<CallLeg> active = service.getActiveCalls();

        assertEquals(2, active.size(), "a local call shows the outgoing and incoming legs");

        CallSession session = outgoing.getSession();
        assertNotNull(session, "the outgoing leg must belong to a CallSession");

        CallLeg incoming = active.stream().filter(c -> c.getDirection() == CallDirection.INCOMING).findFirst()
                                 .orElseThrow();

        // The callee rejects the incoming leg.
        service.rejectCall(incoming.getBackendCallId());

        sipClient.injectEvent(new CallEvent(incoming.getBackendCallId(), 2L, peer, SipCallState.TERMINATED));

        assertTrue(service.getActiveCalls().isEmpty(),
                   "rejecting the incoming leg must also end the outgoing leg of the local call");

        // CSM-007/008: the session must be inactive once both legs ended so no
        // partner leg is left dangling in an inconsistent state.
        assertFalse(session.isActive(), "the session must be inactive once both legs ended (no dangling partner)");
        assertEquals(CallState.ENDED, session.getState(), "the session state must be ENDED");
    }

    @Test
    void endingIncomingLegOfLocalCallNotifiesDialerOfOutgoingLegEnd() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);
        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        // 1003 dials 1002: the dialer tracks this outgoing call.
        CallLeg outgoing = (CallLeg) service.startCall(caller, "1002");

        CallSession session = outgoing.getSession();
        assertNotNull(session, "the outgoing leg must belong to a CallSession");

        String peer = "sip:2_1003@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-1", 2L, peer, SipCallState.INCOMING));

        // 1002 ends the call from its Active calls panel (hangs up the
        // incoming leg). The dialer on 1003 must learn its outgoing leg
        // ended so the CallLeg/Hang up buttons reset.
        sipClient.injectEvent(new CallEvent("inc-1", 2L, peer, SipCallState.TERMINATED));

        assertTrue(listener.receivedEnded(outgoing),
                   "terminating the incoming leg of a local call must notify listeners of the outgoing leg ending");

        // CSM-006: the legacy CallListener still works, and the session that
        // owns both legs must report inactive once they are all ended.
        assertFalse(session.isActive(), "the session must go inactive after the remote leg ends");
    }

    @Test
    void outgoingCallToExternalNumberIsNotPeerLocal() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(new RecordingSipClient(), accounts,
            new NoOpHistoryService());

        CallLeg call = service.startCall(caller, "5551234");

        assertFalse(call.isPeerLocalAccount(), "dialing an unknown number must not flag the" + " call as peer-local");
    }

    @Test
    void establishedPeerUriRefinesLocalGuessToExternal() {

        RecordingSipClient sipClient = new RecordingSipClient();

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        CallLeg call = service.startCall(caller, "1002");

        assertTrue(call.isPeerLocalAccount(), "the dial target matches a local account");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "sip:1002@remote.example.com",
            SipCallState.ESTABLISHED));

        assertFalse(call.isPeerLocalAccount(),
                    "the real peer URI on a foreign host must" + " clear the peer-local flag");
    }

    @Test
    void establishedPeerUriDetectsLocalPeer() {

        RecordingSipClient sipClient = new RecordingSipClient();

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        accounts.createAccount(caller);
        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        CallLeg call = service.startCall(caller, "1999");

        assertFalse(call.isPeerLocalAccount(), "an unknown dial target is not peer-local");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "sip:1002@192.168.0.97",
            SipCallState.ESTABLISHED));

        assertTrue(call.isPeerLocalAccount(), "the peer URI of a local account must flag the" + " call as peer-local");
    }

    @Test
    void incomingCallFromLocalAccountIsPeerLocal() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        accounts.createAccount(account(1L, "2_1003", PEER_DOMAIN));

        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(new RecordingSipClient(), accounts,
            new NoOpHistoryService());

        service.onCallEvent(new CallEvent(CALL_ID, 2L, "sip:1003@192.168.0.97", SipCallState.INCOMING));

        assertTrue(activeCall(service).isPeerLocalAccount(),
                   "an incoming call from the bare number of a" + " local account must be peer-local");
    }

    @Test
    void incomingCallFromExternalPeerIsNotPeerLocal() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        accounts.createAccount(account(1L, "2_1003", PEER_DOMAIN));

        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(new RecordingSipClient(), accounts,
            new NoOpHistoryService());

        service.onCallEvent(new CallEvent(CALL_ID, 2L, "sip:1003@10.0.0.5", SipCallState.INCOMING));

        assertFalse(activeCall(service).isPeerLocalAccount(),
                    "an incoming call from a foreign host must not" + " be peer-local");
    }

    @Test
    void incomingCallWithoutPeerUriIsNotPeerLocal() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        accounts.createAccount(account(1L, "2_1003", PEER_DOMAIN));

        accounts.createAccount(account(2L, "2_1002", PEER_DOMAIN));

        DefaultCallService service = new DefaultCallService(new RecordingSipClient(), accounts,
            new NoOpHistoryService());

        service.onCallEvent(new CallEvent(CALL_ID, 2L, "", SipCallState.INCOMING));

        assertFalse(activeCall(service).isPeerLocalAccount(),
                    "an incoming call without a peer URI must not" + " be peer-local");
    }

    private static CallLeg activeCall(DefaultCallService service) {
        return service.getActiveCalls().get(0);
    }

    /**
     * Transport fake that lets the test inject backend events
     * into the service.
     */
    private static final class RecordingSipClient implements SipClient {

        private SipCallListener callListener;

        // Sequential ids so simultaneous calls never collide in
        // callsByBackendId: the first call keeps returning CALL_ID
        private int nextCallId = 1;

        void injectEvent(CallEvent event) {
            callListener.onCallEvent(event);
        }

        @Override
        public void initialize() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void registerAccount(SipAccountData account) {
        }

        @Override
        public void unregisterAccount(long accountId) {
        }

        @Override
        public String startCall(long accountId, String destination) {
            return "call-" + nextCallId++;
        }

        @Override
        public void answerCall(String callId) {
        }

        @Override
        public void rejectCall(String callId) {
        }

        @Override
        public void endCall(String callId) {
        }

        @Override
        public void holdCall(String callId) {
        }

        @Override
        public void resumeCall(String callId) {
        }

        @Override
        public void setVolume(int volume) {
        }

        @Override
        public void setMicrophoneVolume(int volume) {
        }

        @Override
        public void setMicrophoneMuted(boolean muted) {
        }

        @Override
        public void addRegistrationListener(SipEventListener listener) {
        }

        @Override
        public void addCallListener(SipCallListener listener) {
            this.callListener = listener;
        }
    }

    /**
     * CallLeg listener fake: records every call reported by the service
     * so tests can assert which legs were notified and in which state.
     */
    private static final class RecordingCallListener implements CallListener {

        private final List<CallLeg> calls = new CopyOnWriteArrayList<>();

        @Override
        public void onCallChanged(CallLeg call) {
            calls.add(call);
        }

        boolean receivedEnded(CallLeg target) {
            return calls.stream().anyMatch(c -> c.getId().equals(target.getId()) && c.getState() == CallState.ENDED);
        }
    }

    /**
     * Account service fake: accounts live in a plain map.
     */
    private static final class InMemoryAccounts implements AccountService {

        private final Map<Long, SipAccount> accounts = new HashMap<>();

        @Override
        public SipAccount createAccount(SipAccount account) {
            accounts.put(account.getId(), account);
            return account;
        }

        @Override
        public SipAccount updateAccount(SipAccount account) {
            accounts.put(account.getId(), account);
            return account;
        }

        @Override
        public void deleteAccount(long id) {
            accounts.remove(id);
        }

        @Override
        public List<SipAccount> getAccounts() {

            return List.copyOf(accounts.values());
        }

        @Override
        public void updateStatus(long accountId, com.jsoftsip.core.account.AccountStatus status) {
        }

        @Override
        public void addListener(AccountStatusListener listener) {
        }

        @Override
        public void rotateMasterKey() {
        }

        public void removeListener(AccountStatusListener listener) {
        }

        @Override
        public Optional<SipAccount> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }
    }

    /**
     * History service fake: nothing to record.
     */
    private static final class NoOpHistoryService implements HistoryService {

        @Override
        public void registerFinishedCall(CallLeg call) {
        }

        @Override
        public List<CallHistoryEntry> getHistory() {

            return List.of();
        }

        @Override
        public void clearAll() {
        }

        @Override
        public void addListener(Runnable listener) {
        }
    }

    /**
     * Test that rejecting a forked incoming leg of a local call also ends
     * the outgoing leg and makes the session inactive.
     * This test mirrors the real-world scenario where baresip forks the INVITE
     * to multiple contacts, and the reject must clean up both legs.
     */
    @Test
    void rejectingForkedIncomingLegOfLocalCallAlsoEndsOutgoingLeg() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);

        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // 1003 dials 1002 (peer-local). Outgoing leg on the caller account.
        CallLeg outgoing = (CallLeg) service.startCall(caller, "1002");

        // Baresip forks the INVITE to several contacts of the callee account,
        // each reported as a separate INCOMING event with the same peer URI.
        // The peer URI carries a contact instance token that username
        // normalization does not strip, so the incoming leg correlates to a
        // sibling session instead of the outgoing leg's session. This is the
        // sessionKey divergence from the bug report: only the activeCalls
        // fallback in finishCall can still reach the partner leg.
        String peer = "sip:1003-0x1a2b3c@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("fork-a", 2L, peer, SipCallState.INCOMING));

        sipClient.injectEvent(new CallEvent("fork-b", 2L, peer, SipCallState.INCOMING));

        sipClient.injectEvent(new CallEvent("fork-c", 2L, peer, SipCallState.INCOMING));

        assertEquals(2, service.getActiveCalls().size(), "outgoing + collapsed incoming leg");

        CallLeg incoming = service.getActiveCalls().stream().filter(c -> c.getDirection() == CallDirection.INCOMING)
                                  .findFirst().orElseThrow();

        // Capture the session before the teardown: once both legs end,
        // removeLeg clears the back reference, so the
        // aggregate must be observed through this pre-teardown handle.
        CallSession session = outgoing.getSession();

        // The callee rejects the (single) incoming leg.
        service.rejectCall(incoming.getBackendCallId());

        sipClient.injectEvent(new CallEvent(incoming.getBackendCallId(), 2L, peer, SipCallState.TERMINATED));

        assertTrue(service.getActiveCalls().isEmpty(),
                   "rejecting the forked incoming leg must also end the outgoing leg of the local call");

        assertFalse(session.isActive(), "the session must be inactive once both legs ended (no dangling partner)");

        assertEquals(CallState.ENDED, session.getState(), "the session state must be ENDED");
    }

    @Test
    void finishingExternalCallDoesNotEndUnrelatedCallOnAnotherAccount() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount callerA = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount callerB = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(callerA);

        accounts.createAccount(callerB);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // Two unrelated outgoing calls to external URIs on different
        // accounts. Neither destination designates a local account,
        // so neither leg is a peer-local partner of the other: the
        // activeCalls fallback in finishCall must stay off.
        CallLeg callA = (CallLeg) service.startCall(callerA, "sip:5551234@trunk.example");

        CallLeg callB = (CallLeg) service.startCall(callerB, "sip:999888@other.example");

        // A's call ends. Its session holds a single leg, so only the
        // fallback could reach B's leg.
        sipClient.injectEvent(new CallEvent(callA.getBackendCallId(), 1L, "sip:5551234@trunk.example",
            SipCallState.TERMINATED));

        List<CallLeg> active = service.getActiveCalls();

        assertEquals(1, active.size(), "finishing A's external call must not touch the unrelated call on B");

        assertTrue(active.contains(callB), "B's leg must still be active");

        assertTrue(callB.getState() != CallState.ENDED, "B's leg must not be ENDED");

        assertFalse(active.contains(callA), "A's leg must be gone");
    }

    @Test
    void concurrentPeerLocalCallsDoNotCrossMatch() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount accountA = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount accountB = account(2L, "2_1002", PEER_DOMAIN);

        SipAccount accountC = account(3L, "2_1004", PEER_DOMAIN);

        SipAccount accountD = account(4L, "2_1005", PEER_DOMAIN);

        accounts.createAccount(accountA);

        accounts.createAccount(accountB);

        accounts.createAccount(accountC);

        accounts.createAccount(accountD);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // Two peer-local calls in flight at once (A<->B and C<->D),
        // each with a divergent sessionKey on its incoming leg: the
        // peer URI carries an instance token that normalization does
        // not strip, so only the activeCalls fallback can reach the
        // outgoing leg of each call.
        CallLeg outgoingAB = (CallLeg) service.startCall(accountA, "1002");

        String peerAB = "sip:1003-0x1a2b3c@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-ab", 2L, peerAB, SipCallState.INCOMING));

        CallLeg outgoingCD = (CallLeg) service.startCall(accountC, "1005");

        String peerCD = "sip:1004-0x9f8e7d@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-cd", 4L, peerCD, SipCallState.INCOMING));

        assertEquals(4, service.getActiveCalls().size(), "both peer-local calls show two legs each");

        // B rejects its incoming leg: the first call must end while
        // the second call stays untouched.
        service.rejectCall("inc-ab");

        sipClient.injectEvent(new CallEvent("inc-ab", 2L, peerAB, SipCallState.TERMINATED));

        List<CallLeg> active = service.getActiveCalls();

        assertEquals(2, active.size(), "ending the first call must leave the second call untouched");

        assertFalse(active.contains(outgoingAB), "the outgoing leg of the first call must be gone");

        assertTrue(active.contains(outgoingCD), "the outgoing leg of the second call must still be active");

        assertTrue(outgoingCD.getState() != CallState.ENDED, "the outgoing leg of the second call must not be ENDED");

        assertTrue(active.stream().anyMatch(c -> "inc-cd".equals(c.getBackendCallId())),
                   "the incoming leg of the second call must still be active");
    }

    @Test
    void finishedLegsAreRemovedFromTheirSession() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);

        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // Local call: 1003 dials 1002, both legs share one session
        CallLeg outgoing = (CallLeg) service.startCall(caller, "1002");

        String peer = "sip:2_1003@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-1", 2L, peer, SipCallState.INCOMING));

        CallSession session = outgoing.getSession();

        assertEquals(2, session.getLegs().size(), "the local call groups both legs in one session");

        CallLeg incoming = service.getActiveCalls().stream().filter(c -> c.getDirection() == CallDirection.INCOMING)
                                  .findFirst().orElseThrow();

        // The callee rejects: both legs end through the normal lifecycle
        service.rejectCall(incoming.getBackendCallId());

        sipClient.injectEvent(new CallEvent(incoming.getBackendCallId(), 2L, peer, SipCallState.TERMINATED));

        assertTrue(service.getActiveCalls().isEmpty(), "both legs of the local call must be finished");

        // A finished leg must not linger in the session: a stale ENDED leg
        // makes a redial reuse the session and any first-leg lookup then
        // hits the dead leg instead of the live one.
        assertTrue(session.getLegs().isEmpty(), "finished legs must be removed from their session");
    }

    @Test
    void redialAfterBothLegsEndMintsAFreshSession() {

        InMemoryAccounts accounts = new InMemoryAccounts();

        SipAccount caller = account(1L, "2_1003", PEER_DOMAIN);

        SipAccount callee = account(2L, "2_1002", PEER_DOMAIN);

        accounts.createAccount(caller);

        accounts.createAccount(callee);

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        // First call: 1003 dials 1002 and the callee rejects it, so both
        // legs of the session end
        CallLeg outgoing = (CallLeg) service.startCall(caller, "1002");

        CallSession firstSession = outgoing.getSession();

        String peer = "sip:2_1003@" + PEER_DOMAIN;

        sipClient.injectEvent(new CallEvent("inc-1", 2L, peer, SipCallState.INCOMING));

        CallLeg incoming = service.getActiveCalls().stream().filter(c -> c.getDirection() == CallDirection.INCOMING)
                                  .findFirst().orElseThrow();

        service.rejectCall(incoming.getBackendCallId());

        sipClient.injectEvent(new CallEvent(incoming.getBackendCallId(), 2L, peer, SipCallState.TERMINATED));

        assertTrue(service.getActiveCalls().isEmpty(), "both legs of the first call must be finished");

        // Redialing the same destination must not resurrect the dead
        // session: once every leg ended, the session is evicted and the
        // redial correlates to a freshly minted one.
        CallLeg redialed = (CallLeg) service.startCall(caller, "1002");

        assertNotSame(firstSession, redialed.getSession(),
                      "a redial after the call ended must get a fresh session, not the ended one");
    }
}
