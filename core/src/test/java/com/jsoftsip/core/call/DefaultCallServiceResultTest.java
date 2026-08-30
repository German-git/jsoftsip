package com.jsoftsip.core.call;

import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Service-level proof that every termination path ends with the
 * canonical domain result (CallLeg.resolveResult), including the
 * answered incoming call that the old service-side derivation
 * mislabelled as CANCELLED. The finished call is captured from
 * the history service, the real sink for call results.
 */
class DefaultCallServiceResultTest {

    private static final String CALL_ID = "call-1";

    @Test
    void answeredIncomingCallTerminatesAsAnswered() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        accounts.createAccount(CallServiceTestFixtures.account(1L, "1001"));

        RecordingHistory history = new RecordingHistory();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, history);

        service.onCallEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.INCOMING));

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.ESTABLISHED));

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.TERMINATED));

        assertEquals(CallResult.ANSWERED, history.finished().getResult(),
                     "an answered incoming call must never terminate as CANCELLED");
    }

    @Test
    void unansweredIncomingCallTerminatesAsMissed() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        accounts.createAccount(CallServiceTestFixtures.account(1L, "1001"));

        RecordingHistory history = new RecordingHistory();

        DefaultCallService service = new DefaultCallService(sipClient, accounts, history);

        service.onCallEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.INCOMING));

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "sip:1002@example.com", SipCallState.TERMINATED));

        assertEquals(CallResult.MISSED, history.finished().getResult(), "an unanswered incoming call must be MISSED");
    }

    @Test
    void outgoingCallCancelledDuringRingingTerminatesAsCancelled() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        RecordingHistory history = new RecordingHistory();

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            history);

        service.startCall(CallServiceTestFixtures.account(1L, "1001"), "1002");

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "", SipCallState.RINGING));

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "", SipCallState.TERMINATED));

        assertEquals(CallResult.CANCELLED, history.finished().getResult(),
                     "an outgoing call cancelled while ringing must be CANCELLED");

        assertEquals(0L, history.finished().getDurationSeconds(), "a cancelled call must record no duration");
    }

    @Test
    void outgoingFailedCallTerminatesAsFailed() {

        CallServiceTestFixtures.RecordingSipClient sipClient = new CallServiceTestFixtures.RecordingSipClient();

        RecordingHistory history = new RecordingHistory();

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            history);

        service.startCall(CallServiceTestFixtures.account(1L, "1001"), "1002");

        sipClient.injectEvent(new CallEvent(CALL_ID, 1L, "", SipCallState.FAILED));

        assertEquals(CallResult.FAILED, history.finished().getResult(), "a failed call must be FAILED");
    }

    private static final class RecordingHistory implements HistoryService {

        private CallLeg finished;

        CallLeg finished() {

            return finished;
        }

        @Override
        public void registerFinishedCall(CallLeg call) {

            this.finished = call;
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
}