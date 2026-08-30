package com.jsoftsip.nativebridge.baresip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BaresipTcpConnection implements CtrlConnection {

    private final String host;

    private final int port;

    private Socket socket;

    private InputStream input;

    private OutputStream output;

    private volatile boolean connected;

    private volatile boolean shuttingDown;

    private Thread readerThread;

    private CtrlTcpMessageDispatcher dispatcher;

    /**
     * Constructor for direct wiring with an already built
     * dispatcher.
     */
    public BaresipTcpConnection(String host, int port, CtrlTcpMessageDispatcher dispatcher) {

        this.host = host;

        this.port = port;

        this.dispatcher = dispatcher;
    }

    /**
     * Constructor for the injectable-client path: the
     * dispatcher is registered by BaresipSipClient right
     * after construction via setMessageDispatcher.
     */
    public BaresipTcpConnection(String host, int port) {

        this(host, port, null);
    }

    @Override
    public synchronized void connect() throws IOException {

        if (connected) {
            return;
        }

        socket = new Socket(host, port);

        input = socket.getInputStream();

        output = socket.getOutputStream();

        connected = true;

        // A fresh connection means any previous shutdown
        // intent is stale: reader failures must be reported
        // again until the next intentional disconnect
        shuttingDown = false;

        startReader();
    }

    /**
     * Explicit reconnection after the baresip process was
     * restarted: drops the dead socket and opens a fresh one
     * with its own reader thread. Deliberately NOT wired into
     * ensureConnected, so a command against a dead session
     * still fails fast and only the session-recovery flow
     * decides when a reconnect is safe.
     */
    @Override
    public synchronized void reconnect() throws IOException {

        disconnect();

        connect();
    }

    @Override
    public synchronized void disconnect() {

        // Mark the close as intentional before tearing down:
        // a reader dying on the closed stream is the expected
        // outcome of a shutdown, not a failure to report
        shuttingDown = true;

        connected = false;

        if (readerThread != null) {
            readerThread.interrupt();
        }

        closeQuietly(input);

        closeQuietly(output);

        closeQuietly(socket);
    }

    @Override
    public synchronized void sendCommand(String command) throws IOException {

        ensureConnected();

        // The netstring length is the UTF-8 BYTE length, not the
        // character count: non-ASCII credentials would otherwise
        // produce a wrong prefix that baresip rejects
        byte[] payload = command.getBytes(StandardCharsets.UTF_8);

        output.write((payload.length + ":").getBytes(StandardCharsets.US_ASCII));

        output.write(payload);

        output.write(',');

        output.flush();
    }

    @Override
    public boolean isConnected() {

        return connected;
    }

    @Override
    public synchronized void setMessageDispatcher(CtrlTcpMessageDispatcher dispatcher) {

        this.dispatcher = dispatcher;
    }

    private void startReader() {

        // The reader is bound to the stream of THIS connection
        // attempt, so after a reconnect the dying reader of
        // the old socket can neither dispatch stale payloads
        // nor disconnect the fresh connection
        InputStream stream = input;

        readerThread = Thread.ofVirtual().name("baresip-tcp-reader").start(() -> readLoop(stream));
    }

    private void readLoop(InputStream stream) {

        NetStringReader reader = new NetStringReader(stream);

        while (isCurrentReader(stream)) {

            String payload;

            try {

                payload = reader.read();

            } catch (Throwable failure) {

                // Throwable, not Exception: an allocation failure
                // while reading a corrupt frame is an Error and
                // would otherwise kill the reader thread while
                // connected stays true, leaving a half-open
                // connection whose sends succeed but whose
                // responses never arrive.
                disconnectIfCurrent(stream, failure);

                return;
            }

            if (!isCurrentReader(stream)) {

                return;
            }

            dispatcher.dispatch(payload);
        }
    }

    /**
     * Tears down the connection only when the dying reader is
     * still the current one. The currentness check and the
     * teardown are atomic under the connection lock: a stale
     * reader that wakes between the two would otherwise destroy
     * a fresh socket installed by a concurrent reconnect.
     */
    private synchronized void disconnectIfCurrent(InputStream stream, Throwable failure) {

        if (isCurrentReader(stream)) {

            if (shuttingDown) {

                BaresipLog.debug("Baresip ctrl_tcp reader stopped", failure);

            } else {

                BaresipLog.error("Baresip ctrl_tcp reader thread failed", failure);
            }

            disconnect();
        }
    }

    private synchronized boolean isCurrentReader(InputStream stream) {

        return connected && input == stream;
    }

    private void ensureConnected() throws IOException {

        if (!connected) {

            throw new IOException("Baresip ctrl_tcp is not connected.");
        }
    }

    /**
     * Best-effort close for teardown paths where a close failure has no
     * recovery action, so the silence here is deliberate
     * (see docs/exceptions.md, cross-cutting hygiene).
     */
    private void closeQuietly(AutoCloseable closeable) {

        if (closeable == null) {
            return;
        }

        try {

            closeable.close();

        } catch (Exception ignored) {
        }
    }
}