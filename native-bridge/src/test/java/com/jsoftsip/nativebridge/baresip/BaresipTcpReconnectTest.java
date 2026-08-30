package com.jsoftsip.nativebridge.baresip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaresipTcpReconnectTest {

    private int port;

    private int serverGeneration;

    private ServerSocket server;

    private volatile Socket accepted;

    private BaresipTcpConnection connection;

    private final List<String> responses = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {

        if (connection != null) {
            connection.disconnect();
        }

        closeQuietly(accepted);
        closeQuietly(server);
    }

    @Test
    void reconnectAfterServerDeathRoundTripsOnTheNewServer() throws Exception {

        startServer();

        connection = newConnection(port);

        connection.connect();

        connection.sendCommand("{\"command\":\"sysinfo\"}");

        await(() -> hasResponse("pong-1"), "first server generation must answer");

        // Simulate the settings-apply restart: the ctrl_tcp
        // peer dies and a new process binds the same port
        closeQuietly(accepted);
        server.close();

        startServer();

        // Deliberately no wait for the dead socket to be
        // noticed: reconnect must win the race against the
        // stale reader thread of the old connection
        connection.reconnect();

        connection.sendCommand("{\"command\":\"sysinfo\"}");

        await(() -> hasResponse("pong-2"), "the reconnected socket must round-trip" + " against the new server");

        Thread.sleep(50);

        assertTrue(connection.isConnected(),
                   "the stale reader of the dead socket must not" + " tear down the new connection");
    }

    @Test
    void reconnectFailsWhenNothingListens() throws IOException {

        int freePort;

        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        connection = newConnection(freePort);

        assertThrows(IOException.class, connection::reconnect,
                     "reconnect must surface IO failures instead of" + " hiding them");
        assertFalse(connection.isConnected());
    }

    @Test
    void intentionalDisconnectDoesNotLogReaderFailure() throws Exception {

        startServer();

        connection = newConnection(port);

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.connect();

            // Ordered shutdown path: the app disconnects before
            // killing baresip, so the reader death on the closed
            // socket is intentional
            connection.disconnect();

            Thread.sleep(50);

            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("Baresip ctrl_tcp" + " reader thread" + " failed")),
                       "a reader dying after an intentional" + " disconnect must not log an error");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void unexpectedReaderDeathStillLogsError() throws Exception {

        startServer();

        connection = newConnection(port);

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            connection.connect();

            // The acceptor thread assigns the accepted socket
            // right after accept returns, so wait for it before
            // closing: closing a null reference would silently
            // skip the FIN and the reader would never die
            await(() -> accepted != null, "the server must have accepted the" + " connection");

            // Kill the socket behind the connection's back:
            // the reader dies without any intentional
            // disconnect, exactly like a baresip crash
            closeQuietly(accepted);

            await(() -> !connection.isConnected(), "the reader must tear down the dead" + " connection");

            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("Baresip ctrl_tcp" + " reader thread" + " failed")),
                       "an unexpected reader death must keep" + " logging the error");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    private void startServer() throws IOException {

        serverGeneration++;

        int generation = serverGeneration;

        ServerSocket newServer = new ServerSocket();

        newServer.setReuseAddress(true);

        newServer.bind(new InetSocketAddress("127.0.0.1", port));

        server = newServer;
        port = newServer.getLocalPort();

        Thread acceptor = new Thread(() -> serveGeneration(newServer, generation));

        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * Minimal ctrl_tcp peer: accepts one client and answers
     * every netstring command with a generation-tagged
     * response so tests can tell which server answered.
     */
    private void serveGeneration(ServerSocket serverSocket, int generation) {

        try {

            Socket socket = serverSocket.accept();

            accepted = socket;

            NetStringReader reader = new NetStringReader(socket.getInputStream());

            OutputStream output = socket.getOutputStream();

            while (true) {

                reader.read();

                String payload = "{\"response\":true,\"data\":\"pong-" + generation + "\"}";

                String netstring = payload.length() + ":" + payload + ",";

                output.write(netstring.getBytes(StandardCharsets.UTF_8));

                output.flush();
            }

        } catch (Exception ignored) {
            // the socket was killed to simulate a restart
        }
    }

    private BaresipTcpConnection newConnection(int targetPort) {

        CtrlTcpMessageDispatcher dispatcher = new CtrlTcpMessageDispatcher();

        dispatcher.addResponseListener(responses::add);

        return new BaresipTcpConnection("127.0.0.1", targetPort, dispatcher);
    }

    private boolean hasResponse(String marker) {

        return responses.stream().anyMatch(payload -> payload.contains(marker));
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {

        long deadline = System.currentTimeMillis() + 2_000;

        while (!condition.getAsBoolean()) {

            if (System.currentTimeMillis() > deadline) {
                fail(message);
            }

            Thread.sleep(5);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {

        if (closeable == null) {
            return;
        }

        try {

            closeable.close();

        } catch (Exception ignored) {
        }
    }
}
