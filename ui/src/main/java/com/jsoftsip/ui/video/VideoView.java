package com.jsoftsip.ui.video;

import com.jsoftsip.core.video.FramePipe;
import com.jsoftsip.core.video.VideoFrame;
import com.jsoftsip.core.video.VideoFrameSource;

import javafx.animation.AnimationTimer;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.Optional;

/**
 * Pulls the latest video frame of an account on every FX
 * animation tick and renders it into a reusable WritableImage.
 * The poll is non-blocking: when the pipe is empty, unavailable
 * or the account has no video source, a placeholder image is
 * shown instead. Frame bytes are expected already normalized to
 * BGRA by the native-bridge layer, so this view stays purely
 * JavaFX and never touches baresip types.
 */
public final class VideoView {

    private static final PixelFormat<java.nio.ByteBuffer> BGRA = PixelFormat.getByteBgraInstance();

    private final ImageView imageView = new ImageView();

    private final VideoFrameSource source;

    private final long accountId;

    private final AnimationTimer timer;

    private WritableImage canvas;

    private PixelWriter canvasWriter;

    private int canvasWidth;

    private int canvasHeight;

    private WritableImage placeholder;

    private boolean disposed;

    /**
     * Resolves the video source lazily from the given optional:
     * an empty source (mock backend) renders the placeholder
     * forever without touching any native layer.
     */
    public VideoView(Optional<VideoFrameSource> source, long accountId) {

        this.source = source.orElse(null);
        this.accountId = accountId;
        this.imageView.setPreserveRatio(true);

        this.timer = new AnimationTimer() {

            @Override
            public void handle(long now) {
                render();
            }
        };
    }

    /**
     * The node to place in the scene graph.
     */
    public ImageView node() {
        return imageView;
    }

    /**
     * Starts pulling frames on the FX thread.
     */
    public void start() {
        timer.start();
    }

    /**
     * Stops pulling frames. The last rendered frame stays on
     * the node until the next render call.
     */
    public void stop() {
        timer.stop();
    }

    /**
     * Polls the account pipe once and swaps the frame into the
     * canvas. Exposed for tests and callable from the timer
     * tick: cheap, non-blocking, safe on the FX thread.
     */
    public void render() {

        if (disposed || source == null) {
            showPlaceholder();
            return;
        }

        FramePipe pipe = source.getOrCreateFramePipe(accountId);

        VideoFrame frame = pipe.poll();

        if (frame == null) {
            showPlaceholder();
            return;
        }

        showFrame(frame);
    }

    /**
     * Disposes the view: stops the timer, clears the canvas
     * reference and renders the placeholder. The pipes owned by
     * the source are not touched here.
     */
    public void dispose() {

        disposed = true;

        timer.stop();

        canvas = null;
        canvasWriter = null;

        showPlaceholder();
    }

    private void showFrame(VideoFrame frame) {

        int width = frame.width();
        int height = frame.height();

        if (canvas == null || canvasWidth != width || canvasHeight != height) {

            canvas = new WritableImage(width, height);
            canvasWriter = canvas.getPixelWriter();
            canvasWidth = width;
            canvasHeight = height;
        }

        canvasWriter.setPixels(0, 0, width, height, BGRA, frame.buffer(), frame.stride());

        imageView.setImage(canvas);
    }

    private void showPlaceholder() {

        if (placeholder == null) {
            placeholder = buildPlaceholder();
        }

        imageView.setImage(placeholder);
    }

    private static final int PLACEHOLDER_WIDTH = 320;

    private static final int PLACEHOLDER_HEIGHT = 180;

    private static final int BACKGROUND = 0xFF1E1E1E;

    private static final int CAMERA_BADGE = 0xFF2D2D2D;

    private static final int LENS = 0xFFAAAAAA;

    private WritableImage buildPlaceholder() {

        WritableImage image = new WritableImage(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT);

        PixelWriter writer = image.getPixelWriter();

        // Dark surface so the card reads as "no signal" instead of
        // the transparent hole the old 16x1 image produced.
        fillRect(writer, 0, 0, PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT, BACKGROUND);

        // Stylized camera icon: an off-center badge with a lens.
        int badgeX = PLACEHOLDER_WIDTH / 2 - 24;
        int badgeY = PLACEHOLDER_HEIGHT / 2 - 40;
        fillRoundRect(writer, badgeX, badgeY, 48, 48, 10, CAMERA_BADGE);

        int lensX = PLACEHOLDER_WIDTH / 2 - 14;
        int lensY = badgeY + 12;
        fillOval(writer, lensX, lensY, 28, 28, LENS);

        return image;
    }

    private static void fillRect(PixelWriter writer, int x, int y, int width, int height, int argb) {

        for (int row = y; row < y + height; row++) {

            for (int col = x; col < x + width; col++) {

                writer.setArgb(col, row, argb);
            }
        }
    }

    private static void fillRoundRect(PixelWriter writer, int x, int y, int width, int height, int arc, int argb) {

        fillRect(writer, x + arc, y, width - 2 * arc, height, argb);

        fillRect(writer, x, y + arc, width, height - 2 * arc, argb);

        fillOval(writer, x, y, arc, arc, argb);

        fillOval(writer, x + width - arc, y, arc, arc, argb);

        fillOval(writer, x, y + height - arc, arc, arc, argb);

        fillOval(writer, x + width - arc, y + height - arc, arc, arc, argb);
    }

    private static void fillOval(PixelWriter writer, int cx, int cy, int width, int height, int argb) {

        int radiusX = width / 2;

        int radiusY = height / 2;

        int centerX = cx + radiusX;

        int centerY = cy + radiusY;

        for (int y = cy; y < cy + height; y++) {

            for (int x = cx; x < cx + width; x++) {

                double dx = (x - centerX) / (double) radiusX;

                double dy = (y - centerY) / (double) radiusY;

                if (dx * dx + dy * dy <= 1.0) {

                    writer.setArgb(x, y, argb);
                }
            }
        }
    }
}