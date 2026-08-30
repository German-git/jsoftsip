package com.jsoftsip.core.video;

import java.util.Objects;

/**
 * Immutable video resolution requested for a specific account.
 *
 * <p>The native module is expected to match the nearest supported resolution.
 */
public final class VideoQuality {

    private final int width;
    private final int height;

    public VideoQuality(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Video dimensions must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
    }

    /** Frame width in pixels. */
    public int width() {
        return width;
    }

    /** Frame height in pixels. */
    public int height() {
        return height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VideoQuality)) {
            return false;
        }
        VideoQuality that = (VideoQuality) other;
        return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}