package com.jsoftsip.core.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsKeysTest {

    @Test
    void videoTcpHostKeyMatchesTheBaresipNamespace() {

        assertEquals("baresip.video_tcp.host", SettingsKeys.BARESIP_VIDEO_TCP_HOST);
        assertEquals("127.0.0.1", SettingsKeys.BARESIP_VIDEO_TCP_HOST_DEFAULT);
    }

    @Test
    void videoTcpPortKeyMatchesTheBaresipNamespace() {

        assertEquals("baresip.video_tcp.port", SettingsKeys.BARESIP_VIDEO_TCP_PORT);
        assertEquals("4445", SettingsKeys.BARESIP_VIDEO_TCP_PORT_DEFAULT);
    }

    @Test
    void videoTcpDefaultsDoNotCollideWithCtrlTcp() {

        assertEquals("4444", SettingsKeys.BARESIP_CTRL_TCP_PORT_DEFAULT);
        assertEquals("4445", SettingsKeys.BARESIP_VIDEO_TCP_PORT_DEFAULT);
    }
}