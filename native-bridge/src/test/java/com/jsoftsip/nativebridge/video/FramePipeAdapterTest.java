package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.video.FramePipe;
import com.jsoftsip.core.video.PixelFormat;
import com.jsoftsip.core.video.VideoFrame;
import com.jsoftsip.core.video.VideoQuality;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePipeAdapterTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private static final String BOB_AOR = "sip:bob@example.com";

    /**
     * SipClient stub that only implements the AOR reverse lookup,
     * mirroring BaresipSipClient.setAccountAor semantics.
     */
    private static final class StubSipClient implements SipClient {

        private final Map<String, Long> aorToAccountId = new ConcurrentHashMap<>();

        void setAccountAor(long accountId, String aor) {

            aorToAccountId.put(aor, accountId);
        }

        @Override
        public Long accountIdForAor(String aor) {

            return aorToAccountId.get(aor);
        }

        @Override
        public void initialize() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void registerAccount(SipAccountData account) {
        }

        @Override
        public void unregisterAccount(long accountId) {
        }

        @Override
        public String startCall(long accountId, String destination) {
            return null;
        }

        @Override
        public void answerCall(String callId) {
        }

        @Override
        public void rejectCall(String callId) {
        }

        @Override
        public void endCall(String callId) {
        }

        @Override
        public void holdCall(String callId) {
        }

        @Override
        public void resumeCall(String callId) {
        }

        @Override
        public void setVolume(int volume) {
        }

        @Override
        public void setMicrophoneVolume(int volume) {
        }

        @Override
        public void setMicrophoneMuted(boolean muted) {
        }

        @Override
        public void addRegistrationListener(SipEventListener listener) {
        }

        @Override
        public void addCallListener(SipCallListener listener) {
        }
    }

    private static byte[] yuv420pAllBlack() {

        int stride = 16;
        int height = 2;
        int pixelBytes = stride * height + 2 * (stride / 2) * (height / 2);

        byte[] pixels = new byte[pixelBytes];

        for (int i = 0; i < pixelBytes; i++) {
            pixels[i] = (byte) 16; // black luma, zero chroma
        }

        return pixels;
    }

    @Test
    void routesYuvFramesIntoTheAccountPipe() {

        StubSipClient sipClient = new StubSipClient();
        sipClient.setAccountAor(7, ALICE_AOR);

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        adapter.route(ALICE_AOR, 4, 2, PixelFormat.YUV420P, 123L, 16, yuv420pAllBlack());

        FramePipe pipe = adapter.getOrCreateFramePipe(7);

        VideoFrame frame = pipe.poll();

        assertNotNull(frame);
        assertEquals(4, frame.width());
        assertEquals(2, frame.height());
        assertEquals(PixelFormat.ARGB32, frame.pixelFormat());
        assertEquals(123L, frame.timestampNanos());
        assertEquals(16, frame.stride());
        assertTrue(frame.buffer().isDirect());
        // all-black YUV renders as opaque black BGRA
        assertEquals((byte) 0, frame.buffer().get(0));
        assertEquals((byte) 255, frame.buffer().get(3));
    }

    @Test
    void routesArgbFramesIntoTheAccountPipe() {

        StubSipClient sipClient = new StubSipClient();
        sipClient.setAccountAor(7, ALICE_AOR);

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        byte[] argb = new byte[]{(byte) 255, (byte) 10, (byte) 20, (byte) 30};

        adapter.route(ALICE_AOR, 1, 1, PixelFormat.ARGB32, 456L, 4, argb);

        VideoFrame frame = adapter.getOrCreateFramePipe(7).poll();

        assertNotNull(frame);
        assertEquals(PixelFormat.ARGB32, frame.pixelFormat());
        assertEquals((byte) 30, frame.buffer().get(0), "B");
        assertEquals((byte) 10, frame.buffer().get(2), "R");
    }

    @Test
    void neverLeaksFramesAcrossAccounts() {

        StubSipClient sipClient = new StubSipClient();
        sipClient.setAccountAor(7, ALICE_AOR);
        sipClient.setAccountAor(9, BOB_AOR);

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        adapter.route(ALICE_AOR, 1, 1, PixelFormat.ARGB32, 1L, 4, new byte[]{(byte) 255, 1, 1, 1});

        adapter.route(BOB_AOR, 1, 1, PixelFormat.ARGB32, 2L, 4, new byte[]{(byte) 255, 2, 2, 2});

        FramePipe alice = adapter.getOrCreateFramePipe(7);
        FramePipe bob = adapter.getOrCreateFramePipe(9);

        assertNotSame(alice, bob);

        VideoFrame aliceFrame = alice.poll();
        VideoFrame bobFrame = bob.poll();

        assertEquals(1L, aliceFrame.timestampNanos());
        assertEquals(2L, bobFrame.timestampNanos());
        assertNull(alice.poll(), "alice must not see bob frames");
        assertNull(bob.poll(), "bob must not see alice frames");
    }

    @Test
    void dropsFramesWithUnknownAorWithoutCrashing() {

        StubSipClient sipClient = new StubSipClient();

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        adapter.route("sip:ghost@example.com", 1, 1, PixelFormat.ARGB32, 1L, 4, new byte[]{(byte) 255, 1, 1, 1});

        // nothing to poll anywhere and no exception thrown
        assertNull(adapter.getOrCreateFramePipe(7).poll());
    }

    @Test
    void getOrCreateFramePipeIsStablePerAccount() {

        StubSipClient sipClient = new StubSipClient();

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        assertSame(adapter.getOrCreateFramePipe(7), adapter.getOrCreateFramePipe(7));
    }

    @Test
    void setQualityIsPreparedButUnsupported() {

        StubSipClient sipClient = new StubSipClient();

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        assertThrows(UnsupportedOperationException.class, () -> adapter.setQuality(7, new VideoQuality(640, 480)));
    }

    @Test
    void shutdownClearsEveryPipe() {

        StubSipClient sipClient = new StubSipClient();
        sipClient.setAccountAor(7, ALICE_AOR);

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        adapter.route(ALICE_AOR, 1, 1, PixelFormat.ARGB32, 1L, 4, new byte[]{(byte) 255, 1, 1, 1});

        adapter.shutdown();

        assertNull(adapter.getOrCreateFramePipe(7).poll(), "shutdown must clear buffered frames");

        assertTrue(adapter.getOrCreateFramePipe(7).isShutdown());
    }

    @Test
    void transportFeedReachesTheAccountPipe() throws Exception {

        StubSipClient sipClient = new StubSipClient();
        sipClient.setAccountAor(7, ALICE_AOR);

        FramePipeAdapter adapter = new FramePipeAdapter(sipClient);

        NativeFrameTransport transport = new NativeFrameTransport("127.0.0.1", 0, adapter);

        transport.start();

        try {

            java.net.Socket client = new java.net.Socket(java.net.InetAddress.getByName("127.0.0.1"),
                transport.boundPort());

            try {

                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream data = new java.io.DataOutputStream(out);

                byte[] aor = ALICE_AOR.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                data.writeShort(aor.length);
                data.write(aor);

                int width = 1;
                int height = 1;
                int stride = 4;
                // full YUV420P plane size: Y=4, U=2, V=2
                int pixelBytes = 8;
                data.writeInt(21 + pixelBytes);
                data.writeInt(width);
                data.writeInt(height);
                data.writeByte(PixelFormat.YUV420P.ordinal());
                data.writeLong(999L);
                data.writeInt(stride);
                data.write(new byte[]{16, 16, 16, 16, // black luma
                                      (byte) 128, (byte) 128, // U
                                      (byte) 128, (byte) 128 // V
                });

                client.getOutputStream().write(out.toByteArray());
                client.getOutputStream().flush();

                long deadline = System.currentTimeMillis() + 5_000;
                VideoFrame frame = null;

                while (System.currentTimeMillis() < deadline && frame == null) {

                    frame = adapter.getOrCreateFramePipe(7).poll();
                    Thread.sleep(20);
                }

                assertNotNull(frame, "transport frame must reach the pipe");
                assertEquals(999L, frame.timestampNanos());
                assertEquals((byte) 0, frame.buffer().get(0));

            } finally {
                client.close();
            }

        } finally {
            transport.shutdown();
        }
    }
}