package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.FramePipe;
import com.jsoftsip.core.video.PixelFormat;
import com.jsoftsip.core.video.VideoFrame;
import com.jsoftsip.nativebridge.baresip.BaresipSipClient;
import com.jsoftsip.nativebridge.baresip.FakeCtrlConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The adapter must not pay the full YUV to BGRA
 * conversion for a frame the pipe cannot meaningfully take, so an
 * overloaded or shut down consumer stops burning CPU per frame.
 */
class FramePipeAdapterBackpressureTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private FakeCtrlConnection connection;

    private BaresipSipClient client;

    private FramePipeAdapter adapter;

    @BeforeEach
    void setUp() {

        connection = new FakeCtrlConnection();

        client = new BaresipSipClient(connection);

        adapter = new FramePipeAdapter(client);

        client.setAccountAor(7, ALICE_AOR);
    }

    @AfterEach
    void tearDown() {

        client.shutdown();
    }

    /**
     * While the ring is full the incoming frame would only evict
     * an older one, skipping the conversion leaves the buffered
     * frames untouched so the oldest sentinel survives.
     */
    @Test
    void routeSkipsTheConversionWhileThePipeIsSaturated() {

        FramePipe pipe = adapter.getOrCreateFramePipe(7);

        pipe.push(sentinel(1));
        pipe.push(sentinel(2));
        pipe.push(sentinel(3));

        adapter.route(ALICE_AOR, 4, 2, PixelFormat.YUV420P, 999L, 4, yuv420pPixels());

        assertEquals(1L, pipe.poll().timestampNanos(),
                     "the saturated route must not have displaced the buffered frames");
    }

    @Test
    void routeDoesNotPushIntoAShutDownPipe() {

        FramePipe pipe = adapter.getOrCreateFramePipe(7);

        pipe.shutdown();

        adapter.route(ALICE_AOR, 4, 2, PixelFormat.YUV420P, 999L, 4, yuv420pPixels());

        assertNull(pipe.poll(), "a shut down pipe must stay empty");
    }

    @Test
    void routeConvertsAndPushesWhenThereIsRoom() {

        FramePipe pipe = adapter.getOrCreateFramePipe(7);

        adapter.route(ALICE_AOR, 4, 2, PixelFormat.YUV420P, 999L, 4, yuv420pPixels());

        VideoFrame frame = pipe.poll();

        assertEquals(999L, frame.timestampNanos(), "a healthy pipe takes the converted frame");
        assertEquals(PixelFormat.ARGB32, frame.pixelFormat());
    }

    private static byte[] yuv420pPixels() {

        // 4x2 YUV420P with stride 4: Y plane of 8 bytes plus one
        // chroma row of 2 U and 2 V bytes
        return new byte[12];
    }

    private static VideoFrame sentinel(long id) {

        return new VideoFrame() {

            @Override
            public int width() {
                return 4;
            }

            @Override
            public int height() {
                return 2;
            }

            @Override
            public PixelFormat pixelFormat() {
                return PixelFormat.ARGB32;
            }

            @Override
            public long timestampNanos() {
                return id;
            }

            @Override
            public int stride() {
                return 16;
            }

            @Override
            public ByteBuffer buffer() {
                return ByteBuffer.allocate(32);
            }
        };
    }
}
