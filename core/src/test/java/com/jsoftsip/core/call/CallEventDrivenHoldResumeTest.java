package com.jsoftsip.core.call;

import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jsoftsip.core.call.CallServiceTestFixtures.InMemoryAccounts;
import static com.jsoftsip.core.call.CallServiceTestFixtures.NoOpHistoryService;
import static com.jsoftsip.core.call.CallServiceTestFixtures.RecordingSipClient;
import static com.jsoftsip.core.call.CallServiceTestFixtures.account;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Event-driven hold/resume contract: the
 * hold and resume commands are pure backend passthroughs and
 * the HOLD/CONNECTED state of a leg is driven exclusively by
 * backend events. An ESTABLISHED event whose remote audio is
 * sendonly carries a hold re-INVITE answer while the leg is
 * active: from CONNECTED it is either the confirmation of our
 * own hold request or a remote hold, and from HOLD it repeats
 * that answer. A sendrecv ESTABLISHED on HOLD is the resume
 * confirmation. Both backends must follow the identical policy.
 */
class CallEventDrivenHoldResumeTest {

    private static final String CALL_ID = "call-1";

    private MockCallService mockService;

    @AfterEach
    void tearDown() {

        // The mock service owns a daemon executor, but shutting
        // it down keeps the test fork clean and avoids noise in
        // parallel runs.
        if (mockService != null) {
            mockService.shutdown();
        }
    }

    @Test
    void remoteSendonlyEstablishedMovesConnectedLegToHold() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = service.startCall(account(1L, "user1"), "1002");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

        assertEquals(CallState.CONNECTED, call.getState(), "the plain established event must connect the call");

        listener.notifications.clear();

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, call.getState(),
                     "a sendonly established event on CONNECTED is the hold confirmation or a remote hold"
                         + " and must move the leg to HOLD");

        assertEquals(List.of(call), listener.notifications,
                     "the transition to HOLD must notify listeners exactly once");
    }

    @Test
    void ownHoldCommandThenSendonlyConfirmationDrivesHold() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = service.startCall(account(1L, "user1"), "1002");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

        assertEquals(CallState.CONNECTED, call.getState(), "the plain established event must connect the call");

        listener.notifications.clear();

        service.holdCall(call.getBackendCallId());

        assertEquals(CallState.CONNECTED, call.getState(),
                     "holdCall is a passthrough and must not move the leg out of CONNECTED on its own");

        assertTrue(listener.notifications.isEmpty(), "holdCall must not notify listeners");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, call.getState(),
                     "the sendonly SDP answer to our own hold re-INVITE must drive the leg into HOLD");
    }

    @Test
    void repeatedSendonlyEstablishedOnHoldDoesNotRebound() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        CallLeg call = service.startCall(account(1L, "user1"), "1002");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, call.getState(), "the first sendonly confirmation must put the leg on HOLD");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, call.getState(),
                     "a repeated hold confirmation must keep the leg on HOLD without rebounding to CONNECTED");
    }

    @Test
    void resumeConfirmationConnectsHeldLegAndNotifiesOnce() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = heldCall(service, sipClient);

        listener.notifications.clear();

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, false));

        assertEquals(CallState.CONNECTED, call.getState(),
                     "a sendrecv established event on HOLD is the resume confirmation and must connect the leg");

        assertEquals(1, listener.notifications.size(), "the resume confirmation must notify listeners exactly once");
    }

    @Test
    void preConnectEstablishmentWithSendonlyStillConnects() {

        RecordingSipClient sipClient = new RecordingSipClient();

        InMemoryAccounts accounts = new InMemoryAccounts();

        accounts.createAccount(account(1L, "user1"));

        DefaultCallService service = new DefaultCallService(sipClient, accounts, new NoOpHistoryService());

        sipClient.injectEvent(new CallEvent("incoming-1", 1L, "1001", SipCallState.INCOMING));

        CallLeg call = service.getActiveCalls().get(0);

        assertEquals(CallState.INCOMING, call.getState(), "the incoming event must create the call in INCOMING");

        sipClient.injectEvent(new CallEvent("incoming-1", 1L, "1001", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.CONNECTED, call.getState(),
                     "an establishment with sendonly remote audio must still connect a pre-connect leg");
    }

    @Test
    void mockRemoteSendonlyEstablishedMovesConnectedLegToHold() {

        MockSipClient sipClient = new MockSipClient();

        mockService = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        seedLeg(mockService, CallState.CONNECTED);

        mockService.onCallEvent(new CallEvent(CALL_ID, 1L, "1001", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, getState(CALL_ID, mockService),
                     "a sendonly established event on CONNECTED must move the mock leg to HOLD too");
    }

    @Test
    void mockSendrecvEstablishedOnHoldConnectsAndNotifiesOnce() {

        MockSipClient sipClient = new MockSipClient();

        mockService = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        CallLeg call = seedLeg(mockService, CallState.HOLD);

        RecordingCallListener listener = new RecordingCallListener();

        mockService.addListener(listener);

        mockService.onCallEvent(new CallEvent(CALL_ID, 1L, "1001", SipCallState.ESTABLISHED, false));

        assertEquals(CallState.CONNECTED, call.getState(),
                     "a sendrecv established event on HOLD must connect the mock leg");

        assertEquals(1, listener.notifications.size(), "the resume confirmation must notify listeners exactly once");
    }

    @Test
    void mockHoldResumeCommandsRoundTripThroughEventsWithoutLocalMutation() {

        MockSipClient sipClient = new MockSipClient();

        mockService = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        CallLeg call = seedLeg(mockService, CallState.CONNECTED);

        mockService.holdCall(CALL_ID);

        assertEquals(CallState.HOLD, call.getState(), "the echoed HOLD backend event must drive the leg into HOLD");

        mockService.resumeCall(CALL_ID);

        assertEquals(CallState.CONNECTED, call.getState(),
                     "the echoed ESTABLISHED backend event must confirm the resume");
    }

    /**
     * Runs a connected leg through the hold command and its
     * sendonly confirmation so the caller starts from a held leg
     * exactly as the backend would leave it.
     */
    private static CallLeg heldCall(DefaultCallService service, RecordingSipClient sipClient) {

        CallLeg call = service.startCall(account(1L, "user1"), "1002");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED, true));

        assertEquals(CallState.HOLD, call.getState(), "the leg must start held for this scenario");

        return call;
    }

    /**
     * Registers a leg directly in the service maps with the given
     * state, mirroring how the deleted rebound fixtures seeded
     * scenarios without waiting for simulated timing.
     */
    private static CallLeg seedLeg(MockCallService service, CallState state) {

        CallLeg call = new CallLeg();

        call.setBackendCallId(CALL_ID);
        call.setAccount(account(1L, "user1"));
        call.setDestination("1001");
        call.setDirection(CallDirection.OUTGOING);
        call.setState(state);

        service.activeCalls.add(call);
        service.callsByBackendId.put(CALL_ID, call);

        return call;
    }

    private static CallState getState(String callId, MockCallService service) {

        return service.callsByBackendId.get(callId).getState();
    }

    /**
     * Service listener fake: records every notification.
     */
    private static final class RecordingCallListener implements CallListener {

        private final List<CallLeg> notifications = new ArrayList<>();

        @Override
        public void onCallChanged(CallLeg call) {

            notifications.add(call);
        }
    }
}
