package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipTcpConnectionTest {

    private ServerSocket server;

    private Socket accepted;

    private BaresipTcpConnection connection;

    private CountDownLatch captured;

    private byte[][] received;

    private int byteCount;

    @AfterEach
    void tearDown() {

        if (connection != null) {
            connection.disconnect();
        }

        closeQuietly(accepted);
        closeQuietly(server);
    }

    @Test
    void sendCommandEncodesNonAsciiPayloadWithByteLength() throws Exception {

        startCaptureServer(8);

        connection.sendCommand("josé");

        assertArrayEquals("5:josé,".getBytes(StandardCharsets.UTF_8), awaitReceived(),
                          "the netstring length must be the UTF-8 byte length of the payload");
    }

    @Test
    void sendCommandKeepsAsciiPayloadsByteExact() throws Exception {

        startCaptureServer(7);

        connection.sendCommand("help");

        assertArrayEquals("4:help,".getBytes(StandardCharsets.US_ASCII), awaitReceived(),
                          "ascii commands must keep producing the exact same netstring");
    }

    @Test
    void corruptFrameTearsDownTheConnectionInsteadOfLeavingAZombie() throws Exception {

        // The peer stays open but sends framing garbage: before
        // the reader routed every failure to disconnectIfCurrent,
        // a dead reader left connected=true so sends kept
        // succeeding while responses could never arrive.
        startServerSendingCorruptFrame();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (connection.isConnected() && System.nanoTime() < deadline) {

            Thread.sleep(20);
        }

        assertTrue(!connection.isConnected(), "a corrupt frame must tear down the connection state");
    }

    private void startServerSendingCorruptFrame() throws Exception {

        server = new ServerSocket();

        server.setReuseAddress(true);

        server.bind(new InetSocketAddress("127.0.0.1", 0));

        Thread acceptor = new Thread(() -> {

            try {

                accepted = server.accept();

                accepted.getOutputStream().write("not-a-number:".getBytes(StandardCharsets.UTF_8));

                accepted.getOutputStream().flush();

                // Keep the socket open: the reader must die from
                // the protocol violation alone, not from an EOF

            } catch (Exception ignored) {
            }
        });

        acceptor.setDaemon(true);

        acceptor.start();

        connection = new BaresipTcpConnection("127.0.0.1", server.getLocalPort(), new CtrlTcpMessageDispatcher());

        connection.connect();
    }

    private void startCaptureServer(int expectedBytes) throws Exception {

        byteCount = expectedBytes;

        captured = new CountDownLatch(1);

        received = new byte[1][];

        server = new ServerSocket();

        server.setReuseAddress(true);

        server.bind(new InetSocketAddress("127.0.0.1", 0));

        Thread acceptor = new Thread(this::serveCapture);

        acceptor.setDaemon(true);

        acceptor.start();

        connection = new BaresipTcpConnection("127.0.0.1", server.getLocalPort(), new CtrlTcpMessageDispatcher());

        connection.connect();
    }

    private void serveCapture() {

        try {

            accepted = server.accept();

            InputStream input = accepted.getInputStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            for (int i = 0; i < byteCount; i++) {

                int value = input.read();

                if (value == -1) {
                    break;
                }

                buffer.write(value);
            }

            received[0] = buffer.toByteArray();

        } catch (Exception ignored) {
        }

        captured.countDown();
    }

    private byte[] awaitReceived() throws InterruptedException {

        assertTrue(captured.await(5, TimeUnit.SECONDS), "the server must capture the command bytes");

        return received[0];
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