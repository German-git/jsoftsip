package com.jsoftsip.ui.window;

import java.util.Optional;

/**
 * JavaFX-free value object for the main window rectangle.
 * Persisted as a single comma-separated setting
 * (x,y,width,height) so the whole geometry travels in one
 * row of the settings table.
 */
public record WindowGeometry(double x, double y, double width, double height) {

    private static final int PART_COUNT = 4;

    public String serialize() {

        return x + "," + y + "," + width + "," + height;
    }

    /**
     * Parses a value produced by {@link #serialize()}.
     * Anything malformed (wrong part count, non-numeric or
     * non-finite parts, non-positive size) yields empty so
     * callers fall back to the default window size. A
     * negative position is valid: secondary monitors can
     * sit at negative coordinates.
     */
    public static Optional<WindowGeometry> parse(String raw) {

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        // -1 keeps trailing empty parts so the count check
        // below rejects inputs like "10,20,800,"
        String[] parts = raw.split(",", -1);

        if (parts.length != PART_COUNT) {
            return Optional.empty();
        }

        try {

            double x = parsePart(parts[0]);
            double y = parsePart(parts[1]);
            double width = parsePart(parts[2]);
            double height = parsePart(parts[3]);

            if (width <= 0 || height <= 0) {
                return Optional.empty();
            }

            return Optional.of(new WindowGeometry(x, y, width, height));

        } catch (NumberFormatException exception) {

            return Optional.empty();
        }
    }

    private static double parsePart(String part) {

        double value = Double.parseDouble(part.trim());

        if (!Double.isFinite(value)) {

            throw new NumberFormatException("non-finite value");
        }

        return value;
    }
}
