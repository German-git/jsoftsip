package com.jsoftsip.core.video;

/**
 * Bounded latest-frame-wins buffer for video frames.
 *
 * <p>The ring keeps at most three frames: a producer that is faster
 * than the consumer overwrites the oldest slot instead of blocking
 * (real-time tradeoff, never stalls the native reader thread).
 * The consumer drains the three newest frames in FIFO order with a
 * single non-blocking synchronized poll.
 *
 * <p>Thread safety: every mutating operation synchronizes on the
 * ring array, matching the BaresipTcpConnection convention.
 */
public final class FramePipe {

    private static final int CAPACITY = 3;

    private final VideoFrame[] ring = new VideoFrame[CAPACITY];

    private int tail;

    private int size;

    private boolean shutDown;

    /** Pushes a frame, dropping the oldest when the ring is full. */
    public void push(VideoFrame frame) {

        synchronized (ring) {

            if (shutDown) {
                return;
            }

            ring[tail] = frame;
            tail = (tail + 1) % CAPACITY;

            if (size < CAPACITY) {
                size++;
            }
        }
    }

    /**
     * Returns the oldest buffered frame, or null when the pipe is
     * empty or shut down. The slot is nulled so the buffer becomes
     * garbage-collectible.
     */
    public VideoFrame poll() {

        synchronized (ring) {

            if (size == 0 || shutDown) {
                return null;
            }

            int head = (tail - size + CAPACITY) % CAPACITY;

            VideoFrame frame = ring[head];
            ring[head] = null;
            size--;

            return frame;
        }
    }

    /** Number of slots in the ring. */
    public int capacity() {

        return CAPACITY;
    }

    /**
     * True when the next push would overwrite a buffered frame or
     * the pipe is shut down. Producers with expensive per-frame
     * work use this to skip frames the consumer cannot take in
     * time instead of converting them just to have them dropped.
     */
    public boolean isSaturated() {

        synchronized (ring) {
            return shutDown || size == CAPACITY;
        }
    }

    /** Drops every buffered frame but keeps the pipe usable. */
    public void clear() {

        synchronized (ring) {

            for (int i = 0; i < CAPACITY; i++) {
                ring[i] = null;
            }

            size = 0;
        }
    }

    /**
     * Clears every slot and permanently stops the pipe. Buffered
     * frames become garbage-collectible.
     */
    public void shutdown() {

        synchronized (ring) {

            clear();

            shutDown = true;
        }
    }

    /** Whether {@link #shutdown()} was called. */
    public boolean isShutdown() {

        synchronized (ring) {
            return shutDown;
        }
    }
}