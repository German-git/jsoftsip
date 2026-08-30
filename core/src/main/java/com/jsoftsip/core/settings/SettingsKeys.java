package com.jsoftsip.core.settings;

/**
 * Well-known setting keys and their defaults, used by
 * the composition root (launcher module) to configure
 * the Baresip ctrl_tcp connection and to persist UI
 * preferences such as the volume sliders.
 */
public final class SettingsKeys {

    public static final String BARESIP_CTRL_TCP_HOST = "baresip.ctrl_tcp.host";

    public static final String BARESIP_CTRL_TCP_PORT = "baresip.ctrl_tcp.port";

    public static final String BARESIP_CTRL_TCP_HOST_DEFAULT = "127.0.0.1";

    public static final String BARESIP_CTRL_TCP_PORT_DEFAULT = "4444";

    public static final String BARESIP_VIDEO_TCP_HOST = "baresip.video_tcp.host";

    public static final String BARESIP_VIDEO_TCP_PORT = "baresip.video_tcp.port";

    public static final String BARESIP_VIDEO_TCP_HOST_DEFAULT = "127.0.0.1";

    public static final String BARESIP_VIDEO_TCP_PORT_DEFAULT = "4445";

    public static final String VOLUME_OUTPUT = "volume.output";

    public static final String VOLUME_OUTPUT_DEFAULT = "100";

    public static final String VOLUME_MICROPHONE = "volume.microphone";

    public static final String VOLUME_MICROPHONE_DEFAULT = "100";

    public static final String UI_LOGGING_SAVE_TO_FILE = "ui.logging.save_to_file";

    public static final String UI_LOGGING_SAVE_TO_FILE_DEFAULT = "false";

    public static final String UI_WINDOW_REMEMBER_GEOMETRY = "ui.window.remember_geometry";

    public static final String UI_WINDOW_REMEMBER_GEOMETRY_DEFAULT = "true";

    public static final String UI_WINDOW_GEOMETRY = "ui.window.geometry";

    public static final String UI_WINDOW_GEOMETRY_DEFAULT = "";

    public static final String UI_CONFIRM_EXIT_WITH_CALLS = "ui.confirm_exit_with_calls";

    public static final String UI_CONFIRM_EXIT_WITH_CALLS_DEFAULT = "true";

    public static final String UI_LANGUAGE = "ui.language";

    public static final String UI_LANGUAGE_DEFAULT = "ENGLISH";

    public static final String REGISTRATION_TIMEOUT_SECONDS = "app.registration_timeout_seconds";

    public static final String REGISTRATION_TIMEOUT_SECONDS_DEFAULT = "30";

    private SettingsKeys() {
    }
}
