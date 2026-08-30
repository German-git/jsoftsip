package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipSipClientSessionTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private BaresipSipClient client;

    private final List<CallEvent> callEvents = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {

        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void sessionTerminationSurfacesTerminatedForTrackedCalls() {

        client = newCapturingClient(1);

        client.onEvent(callJson("CALL_INCOMING", "call-1"));
        client.onEvent(callJson("CALL_OUTGOING", "call-2"));

        assertEquals(List.of(SipCallState.INCOMING, SipCallState.DIALING), states(),
                     "baseline: both calls are tracked as alive");

        client.terminateSessionCalls();

        List<CallEvent> terminated = callEvents.stream().filter(event -> event.getState() == SipCallState.TERMINATED)
                                               .toList();

        assertEquals(2, terminated.size(),
                     "every call that died with the process must" + " surface TERMINATED so the UI closes it");
        assertTrue(terminated.stream()
                             .anyMatch(event -> event.getCallId().equals("call-1") && event.getAccountId() == 7
                                 && event.getRemoteUri().equals("sip:bob@example.com")),
                   "the synthesized event must keep call id," + " account and peer of the dead call");
        assertTrue(terminated.stream().anyMatch(event -> event.getCallId().equals("call-2")));
    }

    @Test
    void sessionTerminationIsIdempotent() {

        client = newCapturingClient(1);

        client.onEvent(callJson("CALL_INCOMING", "call-1"));

        client.terminateSessionCalls();

        int eventsAfterFirstPass = callEvents.size();

        client.terminateSessionCalls();

        assertEquals(eventsAfterFirstPass, callEvents.size(),
                     "a rollback restart reuses the same operation," + " so a second pass must be a no-op");
    }

    @Test
    void callsClosedByBaresipEventsAreNotReportedAgain() {

        client = newCapturingClient(1);

        client.onEvent(callJson("CALL_INCOMING", "call-1"));
        client.onEvent(callJson("CALL_CLOSED", "call-1"));

        int eventsAfterClose = callEvents.size();

        client.terminateSessionCalls();

        assertEquals(eventsAfterClose, callEvents.size(),
                     "calls that ended before the restart must not" + " be terminated twice");
    }

    @Test
    void pendingDialFailsFastOnSessionTermination() throws Exception {

        try (ServerSocket silent = new ServerSocket(0)) {

            client = newCapturingClient(silent.getLocalPort());

            client.initialize();

            AtomicReference<RuntimeException> failure = new AtomicReference<>();

            Thread dialer = new Thread(() -> {

                try {

                    client.startCall(7, "sip:bob@example.com");

                } catch (RuntimeException exception) {

                    failure.set(exception);
                }
            });

            dialer.start();

            // Block until the dial actually reached the
            // backend, then keep it silent so the future
            // can only complete through session cleanup
            Socket backendSide = silent.accept();

            new NetStringReader(backendSide.getInputStream()).read();

            client.terminateSessionCalls();

            dialer.join(3000);

            assertFalse(dialer.isAlive(),
                        "a pending dial must not wait for the" + " response timeout once the session" + " is gone");
            assertNotNull(failure.get(), "the pending dial must surface a failure");

            backendSide.close();
        }
    }

    /*
     * normalizeAor asymmetry (REQ-1 backlog item 2): call events
     * resolve the account via raw aorToAccountId.get(event.getAccountAor())
     * while ua events normalize first. If baresip reports a call-event
     * accountaor with params or a default port, the lookup returns null
     * and the event is silently dropped as "ACCOUNT NOT FOUND".
     */

    @Test
    void callEventWithParameterizedAorReachesListeners() {

        client = newCapturingClient(1);

        String payload = "{\"event\":true,\"class\":\"call\",\"type\":\"CALL_INCOMING\","
            + "\"id\":\"call-1\",\"accountaor\":\"sip:alice@example.com;transport=udp\","
            + "\"peeruri\":\"sip:bob@example.com\",\"direction\":\"incoming\",\"param\":\"\"}";

        client.onEvent(payload);

        assertEquals(1, callEvents.size(),
                     "a call event with a parameterized AOR must reach listeners, not be dropped");

        CallEvent event = callEvents.get(0);

        assertEquals((long) 7, event.getAccountId(), "the parameterized AOR must resolve to the registered account id");
    }

    @Test
    void callEventWithDefaultPortAorReachesListeners() {

        client = newCapturingClient(1);

        String payload = "{\"event\":true,\"class\":\"call\",\"type\":\"CALL_INCOMING\","
            + "\"id\":\"call-1\",\"accountaor\":\"sip:alice@example.com:5060\","
            + "\"peeruri\":\"sip:bob@example.com\",\"direction\":\"incoming\",\"param\":\"\"}";

        client.onEvent(payload);

        assertEquals(1, callEvents.size(), "a call event with a default-port AOR must reach listeners, not be dropped");

        CallEvent event = callEvents.get(0);

        assertEquals((long) 7, event.getAccountId(), "the default-port AOR must resolve to the registered account id");
    }

    private BaresipSipClient newCapturingClient(int port) {

        BaresipSipClient newClient = new BaresipSipClient("127.0.0.1", port);

        newClient.addCallListener(callEvents::add);
        newClient.setAccountAor(7, ALICE_AOR);

        return newClient;
    }

    private List<SipCallState> states() {

        return callEvents.stream().map(CallEvent::getState).toList();
    }

    private static String callJson(String type, String callId) {

        return "{\"event\":true,\"class\":\"call\",\"type\":\"" + type + "\",\"id\":\"" + callId
            + "\",\"accountaor\":\"" + ALICE_AOR + "\",\"peeruri\":\"sip:bob@example.com\","
            + "\"direction\":\"incoming\",\"param\":\"\"}";
    }
}
