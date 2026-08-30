package com.jsoftsip.nativebridge.baresip;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory CtrlConnection for pipeline tests. Injected
 * netstrings go through the same NetStringReader used by
 * the real socket reader, so malformed framing behaves
 * exactly like it does on the wire: the offending burst is
 * dropped, the dispatcher survives, and later bursts keep
 * dispatching.
 */
public class FakeCtrlConnection implements CtrlConnection {

    private final List<String> commands = new CopyOnWriteArrayList<>();

    private final AtomicInteger parseFailures = new AtomicInteger();

    private final AtomicInteger reconnectCalls = new AtomicInteger();

    private CtrlTcpMessageDispatcher dispatcher;

    private volatile boolean connected;

    private IOException connectFailure;

    private IOException sendFailure;

    @Override
    public void connect() throws IOException {

        if (connectFailure != null) {
            throw connectFailure;
        }

        connected = true;
    }

    @Override
    public void disconnect() {

        connected = false;
    }

    @Override
    public void reconnect() throws IOException {

        reconnectCalls.incrementAndGet();

        disconnect();

        connect();
    }

    @Override
    public void sendCommand(String command) throws IOException {

        // Record the attempted command before failing, so tests
        // can assert that the command was issued even when the
        // send fails.
        commands.add(command);

        if (sendFailure != null) {
            throw sendFailure;
        }
    }

    @Override
    public boolean isConnected() {

        return connected;
    }

    @Override
    public void setMessageDispatcher(CtrlTcpMessageDispatcher dispatcher) {

        this.dispatcher = dispatcher;
    }

    /**
     * Feeds raw netstrings the way the TCP reader would see
     * them, one burst at a time. A malformed burst is
     * recorded as a parse failure and stops that burst, as
     * the real reader would die on it.
     */
    public void injectNetstring(String netstrings) {

        ByteArrayInputStream stream = new ByteArrayInputStream(netstrings.getBytes(StandardCharsets.UTF_8));

        NetStringReader reader = new NetStringReader(stream);

        while (stream.available() > 0) {

            try {

                String payload = reader.read();

                if (dispatcher != null) {

                    dispatcher.dispatch(payload);
                }

            } catch (Exception exception) {

                parseFailures.incrementAndGet();

                return;
            }
        }
    }

    public List<String> sentCommands() {

        return commands;
    }

    public int parseFailures() {

        return parseFailures.get();
    }

    public int reconnectCalls() {

        return reconnectCalls.get();
    }

    public void scriptConnectFailure(IOException failure) {

        this.connectFailure = failure;
    }

    public void scriptSendFailure(IOException failure) {

        this.sendFailure = failure;
    }
}