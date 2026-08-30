package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsoftsip.core.sip.SipAccountData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BaresipSipClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Live-baresip integration test: requires a real baresip
     * process at 127.0.0.1:4444. Disabled so the FakeCtrlConnection
     * unit tests below run in the default build.
     */
    @Test
    @Disabled("requires live baresip at 127.0.0.1:4444")
    void shouldStartCall() throws Exception {

        BaresipSipClient client = new BaresipSipClient("127.0.0.1", 4444);

        client.initialize();

        String callId = client.startCall(1L, "sip:1002@192.168.0.97");

        System.out.println("CALL ID = " + callId);

        Thread.sleep(30000);

        client.shutdown();
    }

    // -- FakeCtrlConnection unit tests for videodir TX (REQ-8/9/10) --

    private FakeCtrlConnection connection;

    private BaresipSipClient client;

    @BeforeEach
    void setUpVideodir() {

        connection = new FakeCtrlConnection();

        client = new BaresipSipClient(connection);
    }

    @AfterEach
    void tearDownVideodir() {

        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void videodirSendrecvAcceptedReturnsTrue() throws Exception {

        AtomicReference<Boolean> result = new AtomicReference<>();

        Thread tx = new Thread(() -> result.set(client.setVideoTransmissionEnabled(true)));

        tx.start();

        await(() -> connection.sentCommands().size() == 1, "the videodir sendrecv command must reach the connection");

        String command = connection.sentCommands().get(0);

        JsonNode node = MAPPER.readTree(command);

        assertEquals("videodir sendrecv", node.path("command").asText(), "enabling TX must send videodir sendrecv");

        String token = node.path("token").asText();

        assertFalse(token.isEmpty(), "the videodir command must carry a correlation token");

        connection.injectNetstring(netstring("{\"response\":true,\"ok\":true," + "\"token\":\"" + token + "\"}"));

        tx.join(3000);

        assertFalse(tx.isAlive(), "the TX call must complete after the response");

        assertTrue(result.get(), "an accepted sendrecv command must report success");

        assertEquals(0, connection.reconnectCalls(), "videodir must reuse the single ctrl_tcp connection");
    }

    @Test
    void videodirRecvonlyAcceptedReturnsTrue() throws Exception {

        AtomicReference<Boolean> result = new AtomicReference<>();

        Thread tx = new Thread(() -> result.set(client.setVideoTransmissionEnabled(false)));

        tx.start();

        await(() -> connection.sentCommands().size() == 1, "the videodir recvonly command must reach the connection");

        String command = connection.sentCommands().get(0);

        JsonNode node = MAPPER.readTree(command);

        assertEquals("videodir recvonly", node.path("command").asText(), "disabling TX must send videodir recvonly");

        String token = node.path("token").asText();

        connection.injectNetstring(netstring("{\"response\":true,\"ok\":true," + "\"token\":\"" + token + "\"}"));

        tx.join(3000);

        assertFalse(tx.isAlive(), "the TX call must complete after the response");

        assertTrue(result.get(), "an accepted recvonly command must report success");

        assertEquals(0, connection.reconnectCalls(), "videodir must reuse the single ctrl_tcp connection");
    }

    @Test
    void videodirRejectedReturnsFalse() throws Exception {

        AtomicReference<Boolean> result = new AtomicReference<>();

        Thread tx = new Thread(() -> result.set(client.setVideoTransmissionEnabled(true)));

        tx.start();

        await(() -> connection.sentCommands().size() == 1, "the videodir command must reach the connection");

        String command = connection.sentCommands().get(0);

        JsonNode node = MAPPER.readTree(command);

        String token = node.path("token").asText();

        connection.injectNetstring(netstring("{\"response\":true,\"ok\":false," + "\"token\":\"" + token + "\"}"));

        tx.join(3000);

        assertFalse(tx.isAlive(), "the TX call must complete after the rejection");

        assertFalse(result.get(), "a rejected videodir command must report failure");

        assertEquals(0, connection.reconnectCalls(), "a rejected command must not trigger a reconnect");

        assertEquals(0, connection.sentCommands().size() - 1, "only one command should have been sent");
    }

    @Test
    void sendFailureReturnsFalse() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        boolean result = client.setVideoTransmissionEnabled(true);

        assertFalse(result, "a send failure must report false, not crash");

        assertEquals(0, connection.reconnectCalls(), "a send failure must not trigger a reconnect");
    }

    /*
     * FX-thread exception black hole (REQ-1): when ctrl_tcp dies
     * (IOException from sendCommand), the simple-command methods
     * (endCall, answerCall, rejectCall, holdCall, resumeCall) must
     * NOT throw RuntimeException into the JavaFX thread. A dead
     * connection should be logged and swallowed at the SIP-client
     * boundary so the FX event handler thread stays alive.
     */

    @Test
    void endCallWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        client.endCall("test-call-id");

        assertEquals(1, connection.sentCommands().size(), "endCall must still send the hangup command");
    }

    @Test
    void answerCallWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        client.answerCall("test-call-id");

        assertEquals(1, connection.sentCommands().size(), "answerCall must still send the accept command");
    }

    @Test
    void rejectCallWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        client.rejectCall("test-call-id");

        assertEquals(1, connection.sentCommands().size(), "rejectCall must still send the hangup command");
    }

    @Test
    void holdCallWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        client.holdCall("test-call-id");

        assertEquals(1, connection.sentCommands().size(), "holdCall must still send the hold command");
    }

    @Test
    void resumeCallWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        client.resumeCall("test-call-id");

        assertEquals(1, connection.sentCommands().size(), "resumeCall must still send the resume command");
    }

    @Test
    void registerAccountWithDeadConnectionDoesNotThrow() {

        connection.scriptSendFailure(new IOException("simulated disconnect"));

        SipAccountData account = new SipAccountData(1L, "testuser", "testpass", "example.com", "udp");

        client.registerAccount(account);

        assertEquals(1, connection.sentCommands().size(), "registerAccount must still send the uanew command");
    }

    /**
     * Edge case (REQ-9 S2): when baresip never responds, the
     * toggle must time out and return false instead of hanging.
     * Uses a short injected timeout so the test stays fast.
     */
    @Test
    void responseTimeoutReturnsFalse() throws Exception {

        client.setVideodirTimeoutMs(50);

        AtomicReference<Boolean> result = new AtomicReference<>();

        Thread tx = new Thread(() -> result.set(client.setVideoTransmissionEnabled(true)));

        tx.start();

        await(() -> connection.sentCommands().size() == 1, "the videodir command must reach the connection");

        tx.join(3000);

        assertFalse(tx.isAlive(), "the TX call must complete after the timeout");

        assertFalse(result.get(), "a timed-out command must report false");

        assertEquals(0, connection.reconnectCalls(), "a timeout must not trigger a reconnect");
    }

    // -- Command envelope safety and password escaping --

    @Test
    void registerAccountEmbedsPasswordAsValidJsonWithPercentEncodedAuthPass() throws Exception {

        SipAccountData account = new SipAccountData(1L, "user", "p a\"s;w=\\ord", "example.com", "udp");

        client.registerAccount(account);

        String payload = connection.sentCommands().get(0);

        // readTree throws unless the envelope is well formed
        // JSON, which raw concatenation cannot produce for a
        // password containing quotes and backslashes.
        JsonNode node = MAPPER.readTree(payload);

        String command = node.path("command").asText();

        assertEquals("uanew sip:user@example.com;transport=udp;auth_pass=p%20a%22s%3Bw%3D%5Cord", command,
                     "the password must be percent-encoded inside the uanew command");

        String encoded = command.substring(command.indexOf("auth_pass=") + "auth_pass=".length());

        assertEquals("p a\"s;w=\\ord", percentDecode(encoded),
                     "decoding the auth_pass param must yield the original password");
    }

    @Test
    void registerAccountEncodesNonAsciiPasswordBytesAsUtf8() throws Exception {

        SipAccountData account = new SipAccountData(1L, "user", "contraseña", "example.com", "udp");

        client.registerAccount(account);

        JsonNode node = MAPPER.readTree(connection.sentCommands().get(0));

        assertTrue(node.path("command").asText().contains("auth_pass=contrase%C3%B1a"),
                   "non ascii characters must be encoded from their UTF-8 bytes");
    }

    @Test
    void registerAccountKeepsLegacyUriForUnreservedOnlyPassword() {

        SipAccountData account = new SipAccountData(1L, "alice", "s3cret", "example.com", "UDP");

        client.registerAccount(account);

        assertEquals("{\"command\":\"uanew sip:alice@example.com;transport=udp;auth_pass=s3cret\"}",
                     connection.sentCommands().get(0),
                     "unreserved only passwords must keep the legacy payload byte for byte");
    }

    @Test
    void registerAccountRejectsForbiddenCharactersInUsername() {

        assertThrows(IllegalArgumentException.class,
                     () -> client.registerAccount(new SipAccountData(1L, "us;er", "pw", "example.com", "udp")),
                     "username with an addr-param delimiter must be rejected");

        assertThrows(IllegalArgumentException.class,
                     () -> client.registerAccount(new SipAccountData(2L, "us er", "pw", "example.com", "udp")),
                     "username with whitespace must be rejected");

        assertEquals(0, connection.sentCommands().size(), "rejected accounts must not reach ctrl_tcp");
    }

    @Test
    void registerAccountRejectsUnsafeOrBlankDomain() {

        assertThrows(IllegalArgumentException.class,
                     () -> client.registerAccount(new SipAccountData(3L, "user", "pw", "exa mple.com", "udp")),
                     "domain with whitespace must be rejected");

        assertThrows(IllegalArgumentException.class,
                     () -> client.registerAccount(new SipAccountData(4L, "user", "pw", "  ", "udp")),
                     "blank domain must be rejected");

        assertEquals(0, connection.sentCommands().size(), "rejected accounts must not reach ctrl_tcp");
    }

    @Test
    void startCallBuildsValidJsonEnvelopeForDestinationWithQuotes() throws Exception {

        client.registerAccount(new SipAccountData(1L, "alice", "s3cret", "example.com", "UDP"));

        AtomicReference<String> callId = new AtomicReference<>();

        Thread dialer = new Thread(() -> callId.set(client.startCall(1L, "he said \"hi\"")));

        dialer.start();

        await(() -> connection.sentCommands().size() == 2, "the dial command must reach the connection");

        JsonNode node = MAPPER.readTree(connection.sentCommands().get(1));

        assertEquals("dial he said \"hi\"", node.path("command").asText(),
                     "the destination must survive JSON quoting verbatim");

        String token = node.path("token").asText();

        assertFalse(token.isEmpty(), "the dial command must carry a correlation token");

        connection.injectNetstring(netstring("{\"response\":true,\"ok\":true,\"data\":\"call id: call-9\","
            + "\"token\":\"" + token + "\"}"));

        dialer.join(3000);

        assertFalse(dialer.isAlive(), "the dial must complete once the response arrives");

        assertEquals("call-9", callId.get(), "startCall must return the correlated call id");
    }

    @Test
    void authPassEncodingRoundTripsEveryPrintableAsciiAndNonAsciiSample() {

        StringBuilder sample = new StringBuilder();

        for (char c = 0x20; c < 0x7F; c++) {
            sample.append(c);
        }

        sample.append("ñáé");

        assertEquals(sample.toString(), percentDecode(AccountAorRegistry.encodeAuthPass(sample.toString())),
                     "decode(encode(p)) must equal p for every sampled character");
    }

    private static String percentDecode(String value) {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (c == '%' && i + 2 < value.length()) {

                bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));

                i += 2;

            } else {

                bytes.write(c);
            }
        }

        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String netstring(String payload) {

        return payload.length() + ":" + payload + ",";
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
