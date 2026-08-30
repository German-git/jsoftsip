package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipRegistrationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaresipSipClientPipelineTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeCtrlConnection connection;

    private BaresipSipClient client;

    private final List<CallEvent> callEvents = new CopyOnWriteArrayList<>();

    private final List<SipRegistrationEvent> registrationEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {

        connection = new FakeCtrlConnection();

        client = new BaresipSipClient(connection);

        client.addCallListener(callEvents::add);

        client.addRegistrationListener(registrationEvents::add);

        client.setAccountAor(7, ALICE_AOR);
    }

    @AfterEach
    void tearDown() {

        client.shutdown();
    }

    @Test
    void eventBurstForTwoSimultaneousCallsDispatchesCallbacksInOrder() {

        String burst = netstring(callJson("CALL_INCOMING", "call-1"))
            + netstring(callJson("CALL_ESTABLISHED", "call-1")) + netstring(callJson("CALL_INCOMING", "call-2"))
            + netstring(callJson("CALL_ESTABLISHED", "call-2")) + netstring(callJson("CALL_CLOSED", "call-1"))
            + netstring(callJson("CALL_CLOSED", "call-2"));

        connection.injectNetstring(burst);

        assertEquals(List.of(SipCallState.INCOMING, SipCallState.ESTABLISHED, SipCallState.INCOMING,
                             SipCallState.ESTABLISHED, SipCallState.TERMINATED, SipCallState.TERMINATED),
                     states(), "the full netstring burst must drive the" + " listener pipeline in order");

        CallEvent established = callEvents.stream().filter(event -> event.getState() == SipCallState.ESTABLISHED
            && event.getCallId().equals("call-2")).findFirst().orElseThrow();

        assertEquals(7, established.getAccountId(),
                     "the call event must resolve the account from" + " the AOR mapping");

        assertEquals("sip:bob@example.com", established.getRemoteUri(), "the call event must carry the peer uri");
    }

    @Test
    void dialCommandSentVerbatimAndResponseCompletesStartCall() throws Exception {

        client.initialize();

        AtomicReference<String> callId = new AtomicReference<>();

        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        Thread dialer = new Thread(() -> {

            try {

                callId.set(client.startCall(7, "sip:bob@example.com"));

            } catch (RuntimeException exception) {

                failure.set(exception);
            }
        });

        dialer.start();

        await(() -> connection.sentCommands().size() == 1, "the dial command must reach the connection");

        String dialCommand = connection.sentCommands().get(0);

        String token = MAPPER.readTree(dialCommand).path("token").asText();

        assertFalse(token.isEmpty(), "the dial command must carry a correlation" + " token");

        assertEquals("{\"command\":\"dial sip:bob@example.com\"," + "\"token\":\"" + token + "\"}", dialCommand,
                     "the dial command must be sent verbatim");

        connection.injectNetstring(netstring("{\"response\":true,\"ok\":true," + "\"data\":\"call id: call-9\","
            + "\"token\":\"" + token + "\"}"));

        dialer.join(3000);

        assertFalse(dialer.isAlive(), "the dial must complete once the response" + " arrives");

        assertTrue(failure.get() == null,
                   "dial must not fail: " + (failure.get() != null ? failure.get().getMessage() : ""));

        assertEquals("call-9", callId.get(), "startCall must return the call id from the" + " correlated response");
    }

    @Test
    void callCommandsAreSentVerbatim() {

        client.registerAccount(new SipAccountData(7, "alice", "s3cret", "example.com", "UDP"));

        client.answerCall("call-1");
        client.rejectCall("call-1");
        client.endCall("call-1");
        client.holdCall("call-1");
        client.resumeCall("call-1");

        assertEquals(List.of("{\"command\":\"uanew sip:alice@example.com" + ";transport=udp;auth_pass=s3cret\"}",
                             "{\"command\":\"accept call-1\"}", "{\"command\":\"hangup call-1\"}",
                             "{\"command\":\"hangup call-1\"}", "{\"command\":\"hold call-1\"}",
                             "{\"command\":\"resume call-1\"}"),
                     connection.sentCommands(), "every command must be recorded verbatim");
    }

    @Test
    void remoteSdpAnswerCarriesRemoteAudioDirection() {

        connection.injectNetstring(netstring(remoteSdpJson("CALL_REMOTE_SDP", "call-1", "answer", "sendonly")));

        connection.injectNetstring(netstring(remoteSdpJson("CALL_REMOTE_SDP", "call-1", "answer", "sendrecv")));

        CallEvent sendonly = callEvents.stream().filter(CallEvent::isRemoteSendonly).findFirst().orElseThrow();

        assertEquals("call-1", sendonly.getCallId(), "the sendonly event must keep the call id");

        assertEquals(SipCallState.ESTABLISHED, sendonly.getState(), "a sendonly answer must still map to ESTABLISHED");

        CallEvent sendrecv = callEvents.stream()
                                       .filter(event -> !event.isRemoteSendonly() && event.getCallId().equals("call-1"))
                                       .findFirst().orElseThrow();

        assertEquals(SipCallState.ESTABLISHED, sendrecv.getState(),
                     "a sendrecv answer must map to ESTABLISHED" + " without the sendonly flag");
    }

    @Test
    void malformedAndPartialNetstringsDoNotCrashThePipeline() {

        connection.injectNetstring("5:{bad");

        connection.injectNetstring("9999:short,");

        connection.injectNetstring("abc:def,");

        connection.injectNetstring(netstring("{\"event\":true,"));

        assertTrue(connection.parseFailures() >= 3, "malformed netstrings must be reported as" + " parse failures");

        connection.injectNetstring(netstring(callJson("CALL_INCOMING", "call-1")));

        assertEquals(List.of(SipCallState.INCOMING), states(),
                     "the dispatcher must survive malformed input" + " and keep dispatching valid events");
    }

    @Test
    void lifecycleDrivesConnectDisconnectAndReconnect() {

        assertFalse(connection.isConnected());

        client.initialize();

        assertTrue(connection.isConnected(), "initialize must connect the ctrl connection");

        assertTrue(client.reconnect(), "reconnect must round-trip through the" + " connection");

        assertEquals(1, connection.reconnectCalls());

        client.shutdown();

        assertFalse(connection.isConnected(), "shutdown must disconnect the ctrl connection");
    }

    @Test
    void registerFailEventCarriesCodeAndReasonToListeners() {

        connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"REGISTER_FAIL\","
            + "\"accountaor\":\"" + ALICE_AOR + "\",\"code\":403," + "\"reason\":\"Forbidden\"}"));

        assertEquals(1, registrationEvents.size(), "the ua event must reach registration listeners");

        SipRegistrationEvent event = registrationEvents.get(0);

        assertEquals(SipRegistrationState.FAILED, event.getState(), "REGISTER_FAIL must map to FAILED");

        assertEquals(7, event.getAccountId(), "the event must resolve the account from the" + " AOR mapping");

        assertEquals(403, event.getCode(), "the event must carry the response code");

        assertEquals("Forbidden", event.getReason(), "the event must carry the reason phrase");
    }

    @Test
    void registerFailEventWithoutCodeOrReasonCarriesNulls() {

        connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"REGISTER_FAIL\","
            + "\"accountaor\":\"" + ALICE_AOR + "\"}"));

        assertEquals(1, registrationEvents.size(), "the ua event must reach registration listeners");

        SipRegistrationEvent event = registrationEvents.get(0);

        assertEquals(SipRegistrationState.FAILED, event.getState());

        assertNull(event.getCode(), "an absent code must stay null");

        assertNull(event.getReason(), "an absent reason must stay null");
    }

    @Test
    void registerFailEventWithZeroCodeCarriesNull() {

        connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"REGISTER_FAIL\","
            + "\"accountaor\":\"" + ALICE_AOR + "\",\"code\":0," + "\"reason\":\"\"}"));

        SipRegistrationEvent event = registrationEvents.get(0);

        assertNull(event.getCode(), "a zero code must be treated as absent");

        assertNull(event.getReason(), "an empty reason must be treated as absent");
    }

    @Test
    void createAndShutdownUaEventsAreDroppedSilently() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"CREATE\","
                + "\"accountaor\":\"" + ALICE_AOR + "\"}"));

            connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"SHUTDOWN\","
                + "\"accountaor\":\"" + ALICE_AOR + "\"}"));

            assertTrue(appender.list.stream()
                                    .noneMatch(event -> event.getLevel() == Level.WARN
                                        && event.getFormattedMessage().contains("UNKNOWN ua event" + " dropped")),
                       "CREATE and SHUTDOWN are known" + " non-actionable ua events and" + " must drop without warn");

            assertTrue(registrationEvents.isEmpty(),
                       "non-actionable ua events must not reach" + " registration listeners");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void knownNonActionableCallEventsAreDroppedSilently() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.injectNetstring(netstring(callJson("CALL_ANSWERED", "call-1")));

            connection.injectNetstring(netstring(callJson("CALL_RTPESTAB", "call-1")));

            connection.injectNetstring(netstring(callJson("CALL_RTCP", "call-1")));

            assertTrue(appender.list.stream()
                                    .noneMatch(event -> event.getLevel() == Level.WARN
                                        && event.getFormattedMessage().contains("UNKNOWN call event" + " dropped")),
                       "CALL_ANSWERED, CALL_RTPESTAB and CALL_RTCP" + " are known non-actionable call"
                           + " events and must drop without warn");

            assertTrue(callEvents.isEmpty(), "non-actionable call events must not reach" + " call listeners");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void genuinelyUnknownCallEventStillWarns() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.injectNetstring(netstring(callJson("BOGUS_CALL", "call-1")));

            assertTrue(appender.list.stream()
                                    .anyMatch(event -> event.getLevel() == Level.WARN
                                        && event.getFormattedMessage().contains("UNKNOWN call event" + " dropped")),
                       "an unmapped call type must keep the UNKNOWN" + " warn");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void genuinelyUnknownUaEventStillWarns() {

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.injectNetstring(netstring("{\"event\":true,\"class\":\"ua\"," + "\"type\":\"BOGUS_UA\","
                + "\"accountaor\":\"" + ALICE_AOR + "\"}"));

            assertTrue(appender.list.stream()
                                    .anyMatch(event -> event.getLevel() == Level.WARN
                                        && event.getFormattedMessage().contains("UNKNOWN ua event" + " dropped")),
                       "an unmapped ua type must keep the UNKNOWN" + " warn");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void failingCallListenerDoesNotAbortRemainingListeners() throws InterruptedException {

        AtomicReference<CallEvent> secondListenerEvent = new AtomicReference<>();

        client.addCallListener(event -> {
            throw new IllegalStateException("boom");
        });

        client.addCallListener(secondListenerEvent::set);

        connection.injectNetstring(netstring(callJson("CALL_INCOMING", "call-1")));

        await(() -> secondListenerEvent.get() != null, "the second listener must still receive the event");

        assertEquals(SipCallState.INCOMING, secondListenerEvent.get().getState());
    }

    @Test
    void failingRegistrationListenerDoesNotAbortRemainingListeners() throws InterruptedException {

        AtomicReference<SipRegistrationEvent> secondListenerEvent = new AtomicReference<>();

        client.addRegistrationListener(event -> {
            throw new IllegalStateException("boom");
        });

        client.addRegistrationListener(secondListenerEvent::set);

        String uaEvent = "{\"event\":true,\"class\":\"ua\",\"type\":\"REGISTERED\"," + "\"accountaor\":\"" + ALICE_AOR
            + "\",\"code\":200}";

        connection.injectNetstring(netstring(uaEvent));

        await(() -> secondListenerEvent.get() != null, "the second listener must still receive the event");

        assertEquals(SipRegistrationState.REGISTERED, secondListenerEvent.get().getState());
    }

    private List<SipCallState> states() {

        return callEvents.stream().map(CallEvent::getState).toList();
    }

    private static String netstring(String payload) {

        return payload.length() + ":" + payload + ",";
    }

    private static String callJson(String type, String callId) {

        return "{\"event\":true,\"class\":\"call\",\"type\":\"" + type + "\",\"id\":\"" + callId
            + "\",\"accountaor\":\"" + ALICE_AOR + "\",\"peeruri\":\"sip:bob@example.com\","
            + "\"direction\":\"incoming\",\"param\":\"\"}";
    }

    private static String remoteSdpJson(String type, String callId, String param, String remoteAudioDir) {

        return "{\"event\":true,\"class\":\"call\",\"type\":\"" + type + "\",\"id\":\"" + callId
            + "\",\"accountaor\":\"" + ALICE_AOR + "\",\"peeruri\":\"sip:bob@example.com\","
            + "\"direction\":\"incoming\",\"param\":\"" + param + "\",\"remoteaudiodir\":\"" + remoteAudioDir + "\"}";
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {

        long deadline = System.currentTimeMillis() + 3000;

        while (!condition.getAsBoolean()) {

            if (System.currentTimeMillis() > deadline) {
                fail(message);
            }

            Thread.sleep(10);
        }
    }
}