package com.jsoftsip.ui.video;

import com.jsoftsip.core.video.FramePipe;
import com.jsoftsip.core.video.PixelFormat;
import com.jsoftsip.core.video.VideoFrame;
import com.jsoftsip.core.video.VideoFrameSource;
import com.jsoftsip.ui.FxTestToolkit;

import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering is driven by explicit render() calls, so the
 * AnimationTimer is never started in tests.
 */
class VideoViewTest {

    private static final long ACCOUNT_ID = 7L;

    private static FramePipe pipe;

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @Test
    void emptySourceRendersPlaceholder() {

        VideoView view = new VideoView(Optional.empty(), ACCOUNT_ID);

        view.render();

        ImageView node = view.node();

        assertTrue(node.getImage() instanceof WritableImage,
                   "the placeholder must be shown when no source" + " exists");
        assertEquals(320, node.getImage().getWidth(), "the placeholder must be the built-in one");
        assertEquals(180, node.getImage().getHeight(), "the placeholder must match its height");
    }

    @Test
    void nullPollRendersPlaceholder() {

        pipe = new FramePipe();

        VideoView view = viewWithPipe();

        view.render();

        ImageView node = view.node();

        assertTrue(node.getImage() instanceof WritableImage, "an empty pipe must fall back to the placeholder");
        assertEquals(320, node.getImage().getWidth(), "the placeholder must be the built-in one");
        assertEquals(180, node.getImage().getHeight(), "the placeholder must match its height");
    }

    @Test
    void frameIsWrittenIntoTheCanvas() {

        pipe = new FramePipe();

        pipe.push(frame(64, 48, 0));

        VideoView view = viewWithPipe();

        view.render();

        ImageView node = view.node();

        assertEquals(64, node.getImage().getWidth(), "the canvas must match the frame width");
        assertEquals(48, node.getImage().getHeight(), "the canvas must match the frame height");
        assertFalse(node.getImage() instanceof WritableImage && node.getImage().getWidth() == 16,
                    "the frame must replace the placeholder");
    }

    @Test
    void canvasIsReusedAcrossFramesOfTheSameSize() {

        pipe = new FramePipe();

        pipe.push(frame(64, 48, 1));

        VideoView view = viewWithPipe();

        view.render();

        javafx.scene.image.Image first = view.node().getImage();

        pipe.push(frame(64, 48, 2));

        view.render();

        assertSame(first, view.node().getImage(), "same-size frames must reuse the same canvas");
    }

    @Test
    void canvasIsRecreatedWhenFrameSizeChanges() {

        pipe = new FramePipe();

        pipe.push(frame(64, 48, 1));

        VideoView view = viewWithPipe();

        view.render();

        javafx.scene.image.Image first = view.node().getImage();

        pipe.push(frame(128, 96, 2));

        view.render();

        assertNotSame(first, view.node().getImage(), "a new canvas is required for new dimensions");
        assertEquals(128, view.node().getImage().getWidth(), "the new canvas must match the new width");
    }

    @Test
    void placeholderReturnsAfterDispose() {

        pipe = new FramePipe();

        pipe.push(frame(64, 48, 1));

        VideoView view = viewWithPipe();

        view.render();

        view.dispose();

        view.render();

        assertEquals(320, view.node().getImage().getWidth(), "dispose must fall back to the placeholder");
    }

    private static VideoView viewWithPipe() {

        VideoFrameSource source = new VideoFrameSource() {

            @Override
            public FramePipe getOrCreateFramePipe(long accountId) {
                return pipe;
            }

            @Override
            public void setQuality(long accountId, com.jsoftsip.core.video.VideoQuality quality) {
                throw new UnsupportedOperationException();
            }
        };

        return new VideoView(Optional.of(source), ACCOUNT_ID);
    }

    private static VideoFrame frame(int width, int height, long sequence) {

        int rowBytes = width * 4;

        ByteBuffer pixels = ByteBuffer.allocateDirect(rowBytes * height);

        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(i, (byte) (i + sequence));
        }

        return new VideoFrame() {

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public PixelFormat pixelFormat() {
                return PixelFormat.ARGB32;
            }

            @Override
            public int stride() {
                return rowBytes;
            }

            @Override
            public long timestampNanos() {
                return sequence;
            }

            @Override
            public ByteBuffer buffer() {
                return pixels;
            }
        };
    }
}