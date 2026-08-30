package com.jsoftsip.core.settings.baresip;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Typed registry of the baresip settings the app exposes. Each
 * entry carries the settings-table key (baresip.<name>), the
 * baresip config key, a value type with its validation rule, and
 * the default taken from the official baresip v4.6.0 config
 * template. Both the settings UI and the config patch pipeline
 * consume this registry as the single source of truth.
 *
 * <p>{@link #validate(String)} returns null when the raw value is
 * acceptable, otherwise a human readable error message in
 * English.
 */
public enum BaresipOption {

    AUDIO_PLAYER("audio_player", Type.DEVICE, "alsa,default"),

    AUDIO_SOURCE("audio_source", Type.DEVICE, "alsa,default"),

    AUDIO_ALERT("audio_alert", Type.DEVICE, "alsa,default"),

    CALL_LOCAL_TIMEOUT("call_local_timeout", Type.INT_RANGE, "120", 0, 600),

    CALL_MAX_CALLS("call_max_calls", Type.INT_RANGE, "4", 0, 64),

    // empty string disables the timeout, per spec
    RTP_TIMEOUT("rtp_timeout", Type.INT_RANGE, "60", 0, 600, true),

    AUDIO_BUFFER("audio_buffer", Type.RANGE_STR, "20-160"),

    AUDIO_BUFFER_MODE("audio_buffer_mode", Type.ENUM, "fixed", Set.of("fixed", "adaptive")),

    AUDIO_SILENCE("audio_silence", Type.FLOAT_DB, "-35.0"),

    AUDIO_JITTER_BUFFER_MS("audio_jitter_buffer_ms", Type.RANGE_STR, "100-200"),

    AUDIO_JITTER_BUFFER_TYPE("audio_jitter_buffer_type", Type.ENUM, "fixed", Set.of("off", "fixed", "adaptive")),

    AUDIO_JITTER_BUFFER_SIZE("audio_jitter_buffer_size", Type.INT_RANGE, "50", 1, 1000),

    CALL_ACCEPT("call_accept", Type.YES_NO, "no"),

    SIP_LISTEN("sip_listen", Type.HOST_PORT, "0.0.0.0:5060"),

    VIDEO_CODEC("video_codec", Type.ENUM, "h264", Set.of("h264", "vp8", "h265")),

    VIDEO_RESOLUTION("video_resolution", Type.ENUM, "1280x720",
        Set.of("176x144", "320x240", "640x480", "1280x720", "1920x1080")),

    VIDEO_BITRATE("video_bitrate", Type.INT_RANGE, "1024", 64, 16384),

    VIDEO_FPS("video_fps", Type.INT_RANGE, "30", 1, 60);

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)-(\\d+)$");

    // host is an IPv4, a bracketed IPv6 or a hostname
    // port is the single capturing group
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile("^(?:" + "\\d{1,3}(?:\\.\\d{1,3}){3}"
        + "|\\[[0-9a-fA-F:]+\\]" + "|[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?" + "):(\\d{1,5})$");

    // device strings must not contain chars that would break the
    // config line format or enable config injection
    private static final Pattern DEVICE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9,._/-]*$");

    private final String configKey;

    private final Type type;

    private final String defaultValue;

    private final int intMin;

    private final int intMax;

    private final boolean emptyAllowed;

    private final Set<String> enumValues;

    BaresipOption(String configKey, Type type, String defaultValue) {

        this(configKey, type, defaultValue, 0, 0, false, Set.of());
    }

    BaresipOption(String configKey, Type type, String defaultValue, int intMin, int intMax) {

        this(configKey, type, defaultValue, intMin, intMax, false, Set.of());
    }

    BaresipOption(String configKey, Type type, String defaultValue, int intMin, int intMax, boolean emptyAllowed) {

        this(configKey, type, defaultValue, intMin, intMax, emptyAllowed, Set.of());
    }

    BaresipOption(String configKey, Type type, String defaultValue, Set<String> enumValues) {

        this(configKey, type, defaultValue, 0, 0, false, enumValues);
    }

    BaresipOption(String configKey, Type type, String defaultValue, int intMin, int intMax, boolean emptyAllowed,
                  Set<String> enumValues) {

        this.configKey = configKey;
        this.type = type;
        this.defaultValue = defaultValue;
        this.intMin = intMin;
        this.intMax = intMax;
        this.emptyAllowed = emptyAllowed;
        this.enumValues = enumValues;
    }

    /**
     * Key used in the settings table, always baresip.<configKey>.
     */
    public String settingsKey() {

        return "baresip." + configKey;
    }

    /**
     * Key as written in the baresip config file.
     */
    public String configKey() {

        return configKey;
    }

    public Type type() {

        return type;
    }

    /**
     * Default from the official baresip v4.6.0 config template.
     */
    public String defaultValue() {

        return defaultValue;
    }

    /**
     * Validates a raw value as typed by the user. Returns null
     * when valid, otherwise an English error message that names
     * the config key.
     */
    public String validate(String raw) {

        if (raw == null) {
            return configKey + ": value must not be null";
        }

        if (raw.isEmpty() && emptyAllowed) {
            return null;
        }

        return switch (type) {
            case INT_RANGE -> validateIntRange(raw);
            case RANGE_STR -> validateRangeString(raw);
            case FLOAT_DB -> validateFloatDb(raw);
            case ENUM -> enumValues.contains(raw) ? null : configKey + ": must be one of " + enumValues;
            case YES_NO -> ("yes".equals(raw) || "no".equals(raw)) ? null : configKey + ": must be yes or no";
            case HOST_PORT -> validateHostPort(raw);
            case DEVICE -> DEVICE_PATTERN.matcher(raw).matches() ? null : configKey + ": invalid device name";
        };
    }

    private String validateIntRange(String raw) {

        final int parsed;

        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return configKey + ": must be an integer";
        }

        if (parsed < intMin || parsed > intMax) {
            return configKey + ": must be between " + intMin + " and " + intMax;
        }

        return null;
    }

    private String validateRangeString(String raw) {

        var matcher = RANGE_PATTERN.matcher(raw.trim());

        if (!matcher.matches()) {
            return configKey + ": must be a range like 20-160";
        }

        int low = Integer.parseInt(matcher.group(1));
        int high = Integer.parseInt(matcher.group(2));

        if (low > high) {
            return configKey + ": range start must not exceed range end";
        }

        return null;
    }

    private String validateFloatDb(String raw) {

        final double parsed;

        try {
            parsed = Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            return configKey + ": must be a number in dB";
        }

        if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < -200.0 || parsed > 0.0) {
            return configKey + ": must be between -200.0 and 0.0 dB";
        }

        return null;
    }

    private String validateHostPort(String raw) {

        var matcher = HOST_PORT_PATTERN.matcher(raw.trim());

        if (!matcher.matches()) {
            return configKey + ": must be host:port";
        }

        int port = Integer.parseInt(matcher.group(1));

        if (port < 1 || port > 65535) {
            return configKey + ": port must be between 1 and 65535";
        }

        return null;
    }

    /**
     * Value kinds supported by the registry. Each kind maps to a
     * validation rule and drives the widget the UI renders.
     */
    public enum Type {
        DEVICE, INT_RANGE, RANGE_STR, FLOAT_DB, ENUM, YES_NO, HOST_PORT
    }
}
