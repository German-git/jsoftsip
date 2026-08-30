package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.PixelFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeFrameTransportTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private static final String BOB_AOR = "sip:bob@example.com";

    private final List<NativeFrameTransportTest.RoutedFrame> routed = new CopyOnWriteArrayList<>();

    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    private NativeFrameTransport transport;

    private final List<NativeFrameTransport> transports = new CopyOnWriteArrayList<>();

    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {

        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }

        for (NativeFrameTransport t : transports) {
            t.shutdown();
        }
    }

    private NativeFrameTransport startTransport() {

        NativeFrameTransport t = new NativeFrameTransport("127.0.0.1", 0, this::record);

        t.start();
        transports.add(t);
        return t;
    }

    private void record(String aor, int width, int height, PixelFormat pixelFormat, long timestampNanos, int stride,
                        byte[] pixels) {

        routed.add(new RoutedFrame(aor, width, height, pixelFormat, timestampNanos, stride, pixels));
    }

    private Socket connect(int port) throws IOException {

        Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);

        sockets.add(socket);
        return socket;
    }

    private static byte[] frameBytes(String aor) throws IOException {

        int width = 4;
        int height = 2;
        int stride = 16;
        int pixelBytes = stride * height + 2 * (stride / 2) * (height / 2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aorBytes = aor.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aorBytes.length);
        data.write(aorBytes);

        data.writeInt(21 + pixelBytes);
        data.writeInt(width);
        data.writeInt(height);
        data.writeByte(PixelFormat.YUV420P.ordinal());
        data.writeLong(9_876_543_210L);
        data.writeInt(stride);

        for (int i = 0; i < pixelBytes; i++) {
            data.writeByte(i);
        }

        return out.toByteArray();
    }

    private static void await(CheckedBoolean condition) {

        long deadline = System.currentTimeMillis() + 1_000;

        while (System.currentTimeMillis() < deadline) {

            if (condition.get()) {
                return;
            }

            try {
                Thread.sleep(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        throw new AssertionError("condition not met within 1s");
    }

    /**
     * Waits until the server side closed its end, detected as an
     * EOF on the client stream.
     */
    private static void awaitServerClosed(Socket socket) {

        await(() -> {

            try {
                return socket.getInputStream().read() == -1;
            } catch (IOException exception) {
                return true;
            }
        });
    }

    @FunctionalInterface
    private interface CheckedBoolean {

        boolean get();
    }

    private record RoutedFrame(String aor, int width, int height, PixelFormat pixelFormat, long timestampNanos,
        int stride, byte[] pixels) {
    }

    @Test
    void bindsAndAcceptsAFrameFromAClient() throws IOException {

        NativeFrameTransport t = startTransport();
        assertTrue(t.isRunning());

        Socket client = connect(t.boundPort());
        client.getOutputStream().write(frameBytes(ALICE_AOR));
        client.getOutputStream().flush();

        await(() -> routed.size() == 1);

        RoutedFrame frame = routed.get(0);
        assertEquals(ALICE_AOR, frame.aor());
        assertEquals(4, frame.width());
        assertEquals(2, frame.height());
        assertEquals(PixelFormat.YUV420P, frame.pixelFormat());
        assertEquals(9_876_543_210L, frame.timestampNanos());
        assertEquals(16, frame.stride());
        assertEquals(48, frame.pixels().length);
    }

    @Test
    void routesFramesOfDifferentAorsWithoutCrossTalk() throws IOException {

        NativeFrameTransport t = startTransport();

        Socket client = connect(t.boundPort());
        OutputStream out = client.getOutputStream();
        out.write(frameBytes(ALICE_AOR));
        out.write(frameBytes(BOB_AOR));
        out.flush();

        await(() -> routed.size() == 2);

        assertEquals(ALICE_AOR, routed.get(0).aor());
        assertEquals(BOB_AOR, routed.get(1).aor());
    }

    @Test
    void keepsServingAfterAClientClosesGracefully() throws IOException {

        NativeFrameTransport t = startTransport();

        Socket first = connect(t.boundPort());
        first.getOutputStream().write(frameBytes(ALICE_AOR));
        first.getOutputStream().flush();

        await(() -> routed.size() == 1);

        first.close();

        Socket second = connect(t.boundPort());
        second.getOutputStream().write(frameBytes(BOB_AOR));
        second.getOutputStream().flush();

        await(() -> routed.size() == 2);

        assertEquals(BOB_AOR, routed.get(1).aor());
    }

    @Test
    void closesTheOffendingSocketAndReAcceptsAfterAMalformedFrame() throws IOException {

        NativeFrameTransport t = startTransport();

        Socket bad = connect(t.boundPort());
        // aorLen=0, then a length of 5 that can never cover the
        // 21 byte header: the transport must reject the frame
        bad.getOutputStream().write(new byte[]{0, 0, 0, 0, 0, 5, 1, 2, 3, 4, 5});
        bad.getOutputStream().flush();

        // the transport must close the malformed socket and keep
        // accepting new connections
        awaitServerClosed(bad);

        Socket good = connect(t.boundPort());
        good.getOutputStream().write(frameBytes(ALICE_AOR));
        good.getOutputStream().flush();

        await(() -> routed.size() == 1);

        assertEquals(ALICE_AOR, routed.get(0).aor());
    }

    @Test
    void truncatedStreamClosesTheSocketAndKeepsRunning() throws IOException {

        NativeFrameTransport t = startTransport();

        byte[] full = frameBytes(ALICE_AOR);
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 4);

        Socket bad = connect(t.boundPort());
        bad.getOutputStream().write(truncated);
        bad.getOutputStream().flush();
        // half-close so the reader sees the EOF that reveals the
        // truncation, exactly like a crashed baresip would
        bad.shutdownOutput();

        awaitServerClosed(bad);
        assertTrue(t.isRunning());
    }

    @Test
    void bindFailureDegradesToNotRunningWithoutCrashing() throws IOException {

        NativeFrameTransport first = startTransport();

        NativeFrameTransport second = new NativeFrameTransport("127.0.0.1", first.boundPort(), this::record);

        second.start();
        transports.add(second);

        assertFalse(second.isRunning());
    }

    @Test
    void shutdownStopsAcceptingAndClosesActiveSockets() throws IOException {

        NativeFrameTransport t = startTransport();

        Socket client = connect(t.boundPort());
        client.getOutputStream().write(frameBytes(ALICE_AOR));
        client.getOutputStream().flush();

        await(() -> routed.size() == 1);

        t.shutdown();

        awaitServerClosed(client);

        assertFalse(t.isRunning());

        assertFalse(failure.get() != null, "no reader failure may escape shutdown");
    }

    @Test
    void readerFailuresNeverEscapeTheTransport() throws IOException {

        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        NativeFrameTransport t = new NativeFrameTransport("127.0.0.1", 0, (aor, w, h, fmt, ts, stride, pixels) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            record(aor, w, h, fmt, ts, stride, pixels);
        });

        t.start();
        transports.add(t);

        Socket first = connect(t.boundPort());
        first.getOutputStream().write(frameBytes(ALICE_AOR));
        first.getOutputStream().flush();

        // the transport must survive a crashing router, close the
        // offending socket and still accept the next client
        awaitServerClosed(first);

        Socket second = connect(t.boundPort());
        second.getOutputStream().write(frameBytes(BOB_AOR));
        second.getOutputStream().flush();

        await(() -> routed.size() == 1);

        assertEquals(BOB_AOR, routed.get(0).aor());
        assertTrue(t.isRunning());
    }
}