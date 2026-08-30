package com.jsoftsip.core.call;

import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The call duration must start at ESTABLISHED for both
 * directions: counting ringing time on outgoing calls inflated
 * the reported duration. Incoming calls already behaved this
 * way, the outgoing path is the one unified here.
 */
class DefaultCallServiceDurationTest {

    private static final String CALL_ID = "call-1";

    @Test
    void outgoingCallDurationDoesNotStartAtDialTime() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            new CallServiceTestFixtures.NoOpHistoryService());

        CallLeg call = service.startCall(CallServiceTestFixtures.account(1L, "1001"), "1002");

        assertNull(call.getStartedAt(), "duration must not start while dialing");
    }

    @Test
    void outgoingCallDurationStartsWhenEstablished() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            new CallServiceTestFixtures.NoOpHistoryService());

        CallLeg call = service.startCall(CallServiceTestFixtures.account(1L, "1001"), "1002");

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.ESTABLISHED));

        assertNotNull(call.getStartedAt(), "duration must start when the call is established");
    }

    @Test
    void incomingCallDurationDoesNotStartAtIncomingEvent() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        accounts.createAccount(CallServiceTestFixtures.account(1L, "1001"));

        DefaultCallService service = new DefaultCallService(sipClient, accounts,
            new CallServiceTestFixtures.NoOpHistoryService());

        service.onCallEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.INCOMING));

        CallLeg call = service.getActiveCalls().get(0);

        assertNull(call.getStartedAt(), "duration must not start while the call is still incoming");
    }

    @Test
    void incomingCallDurationStartsWhenEstablished() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        accounts.createAccount(CallServiceTestFixtures.account(1L, "1001"));

        DefaultCallService service = new DefaultCallService(sipClient, accounts,
            new CallServiceTestFixtures.NoOpHistoryService());

        service.onCallEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.INCOMING));

        CallLeg call = service.getActiveCalls().get(0);

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.ESTABLISHED));

        assertNotNull(call.getStartedAt(), "duration must start when the call is established");
    }
}