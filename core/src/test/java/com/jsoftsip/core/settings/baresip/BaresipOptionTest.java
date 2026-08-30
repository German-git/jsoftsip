package com.jsoftsip.core.settings.baresip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipOptionTest {

    @Test
    void registryContainsExactlyEighteenOptions() {

        assertEquals(18, BaresipOption.values().length);
    }

    @Test
    void settingsKeysAreUniqueAndPrefixed() {

        Set<String> settingsKeys = new HashSet<>();
        Set<String> configKeys = new HashSet<>();

        for (BaresipOption option : BaresipOption.values()) {

            assertTrue(option.settingsKey().startsWith("baresip."), "settings key must use the baresip. prefix");

            assertEquals("baresip." + option.configKey(), option.settingsKey(),
                         "settings key must be baresip.<configKey>");

            assertTrue(settingsKeys.add(option.settingsKey()), "duplicate settings key: " + option.settingsKey());

            assertTrue(configKeys.add(option.configKey()), "duplicate config key: " + option.configKey());
        }
    }

    @Test
    void defaultsMatchOfficialTemplate() {

        assertEquals("alsa,default", BaresipOption.AUDIO_PLAYER.defaultValue());
        assertEquals("alsa,default", BaresipOption.AUDIO_SOURCE.defaultValue());
        assertEquals("alsa,default", BaresipOption.AUDIO_ALERT.defaultValue());
        assertEquals("120", BaresipOption.CALL_LOCAL_TIMEOUT.defaultValue());
        assertEquals("4", BaresipOption.CALL_MAX_CALLS.defaultValue());
        assertEquals("60", BaresipOption.RTP_TIMEOUT.defaultValue());
        assertEquals("20-160", BaresipOption.AUDIO_BUFFER.defaultValue());
        assertEquals("fixed", BaresipOption.AUDIO_BUFFER_MODE.defaultValue());
        assertEquals("-35.0", BaresipOption.AUDIO_SILENCE.defaultValue());
        assertEquals("100-200", BaresipOption.AUDIO_JITTER_BUFFER_MS.defaultValue());
        assertEquals("fixed", BaresipOption.AUDIO_JITTER_BUFFER_TYPE.defaultValue());
        assertEquals("50", BaresipOption.AUDIO_JITTER_BUFFER_SIZE.defaultValue());
        assertEquals("no", BaresipOption.CALL_ACCEPT.defaultValue());
        assertEquals("0.0.0.0:5060", BaresipOption.SIP_LISTEN.defaultValue());
        assertEquals("h264", BaresipOption.VIDEO_CODEC.defaultValue());
        assertEquals("1280x720", BaresipOption.VIDEO_RESOLUTION.defaultValue());
        assertEquals("1024", BaresipOption.VIDEO_BITRATE.defaultValue());
        assertEquals("30", BaresipOption.VIDEO_FPS.defaultValue());
    }

    @ParameterizedTest
    @EnumSource(BaresipOption.class)
    void everyDefaultPassesItsOwnValidation(BaresipOption option) {

        assertNull(option.validate(option.defaultValue()), "default of " + option.configKey() + " must be valid");
    }

    @ParameterizedTest
    @EnumSource(BaresipOption.class)
    void nullValueIsRejected(BaresipOption option) {

        assertNotNull(option.validate(null), "null must be rejected for " + option.configKey());
    }

    @Test
    void intRangeAcceptsBoundariesAndMiddle() {

        assertNull(BaresipOption.CALL_LOCAL_TIMEOUT.validate("0"));
        assertNull(BaresipOption.CALL_LOCAL_TIMEOUT.validate("120"));
        assertNull(BaresipOption.CALL_LOCAL_TIMEOUT.validate("600"));
        assertNull(BaresipOption.CALL_MAX_CALLS.validate("1"));
        assertNull(BaresipOption.CALL_MAX_CALLS.validate("64"));
        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_SIZE.validate("50"));
    }

    @Test
    void intRangeRejectsOutOfRangeAndGarbage() {

        assertNotNull(BaresipOption.CALL_LOCAL_TIMEOUT.validate("-1"));
        assertNotNull(BaresipOption.CALL_LOCAL_TIMEOUT.validate("601"));
        assertNotNull(BaresipOption.CALL_MAX_CALLS.validate("-1"));
        assertNotNull(BaresipOption.CALL_MAX_CALLS.validate("65"));
        assertNotNull(BaresipOption.CALL_MAX_CALLS.validate("abc"));
        assertNotNull(BaresipOption.CALL_MAX_CALLS.validate("4.5"));
        assertNotNull(BaresipOption.CALL_MAX_CALLS.validate(""));
    }

    @Test
    void rtpTimeoutAcceptsEmptyAsDisabled() {

        assertNull(BaresipOption.RTP_TIMEOUT.validate(""));
        assertNull(BaresipOption.RTP_TIMEOUT.validate("0"));
        assertNull(BaresipOption.RTP_TIMEOUT.validate("30"));
        assertNotNull(BaresipOption.RTP_TIMEOUT.validate("-1"));
        assertNotNull(BaresipOption.RTP_TIMEOUT.validate("abc"));
    }

    @Test
    void rangeStringAcceptsAscendingRange() {

        assertNull(BaresipOption.AUDIO_BUFFER.validate("20-160"));
        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_MS.validate("100-200"));
        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_MS.validate("0-0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "20", "160-20", "20-160-300", "-160", "20 -160", ""})
    void rangeStringRejectsMalformedValues(String raw) {

        assertNotNull(BaresipOption.AUDIO_BUFFER.validate(raw), "must reject: [" + raw + "]");
    }

    @Test
    void floatDbAcceptsNegativeAndZero() {

        assertNull(BaresipOption.AUDIO_SILENCE.validate("-35.0"));
        assertNull(BaresipOption.AUDIO_SILENCE.validate("0.0"));
        assertNull(BaresipOption.AUDIO_SILENCE.validate("-200.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "abc", "", "-201.0", "NaN", "Infinity"})
    void floatDbRejectsPositiveAndGarbage(String raw) {

        assertNotNull(BaresipOption.AUDIO_SILENCE.validate(raw), "must reject: [" + raw + "]");
    }

    @Test
    void enumAcceptsOnlyListedValues() {

        assertNull(BaresipOption.AUDIO_BUFFER_MODE.validate("fixed"));
        assertNull(BaresipOption.AUDIO_BUFFER_MODE.validate("adaptive"));
        assertNotNull(BaresipOption.AUDIO_BUFFER_MODE.validate("off"));
        assertNotNull(BaresipOption.AUDIO_BUFFER_MODE.validate("FIXED"));

        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_TYPE.validate("off"));
        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_TYPE.validate("fixed"));
        assertNull(BaresipOption.AUDIO_JITTER_BUFFER_TYPE.validate("adaptive"));
        assertNotNull(BaresipOption.AUDIO_JITTER_BUFFER_TYPE.validate("auto"));
    }

    @Test
    void yesNoAcceptsOnlyYesOrNo() {

        assertNull(BaresipOption.CALL_ACCEPT.validate("yes"));
        assertNull(BaresipOption.CALL_ACCEPT.validate("no"));
        assertNotNull(BaresipOption.CALL_ACCEPT.validate("true"));
        assertNotNull(BaresipOption.CALL_ACCEPT.validate("YES"));
        assertNotNull(BaresipOption.CALL_ACCEPT.validate(""));
    }

    @Test
    void hostPortAcceptsIpAndHostname() {

        assertNull(BaresipOption.SIP_LISTEN.validate("0.0.0.0:5060"));
        assertNull(BaresipOption.SIP_LISTEN.validate("127.0.0.1:4444"));
        assertNull(BaresipOption.SIP_LISTEN.validate("[::1]:5060"));
        assertNull(BaresipOption.SIP_LISTEN.validate("sip.example.com:5060"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "0.0.0.0:", ":5060", "host:abc", "host:0", "host:65536", "host:5060 extra", ""})
    void hostPortRejectsMalformedValues(String raw) {

        assertNotNull(BaresipOption.SIP_LISTEN.validate(raw), "must reject: [" + raw + "]");
    }

    @Test
    void deviceAcceptsDriverAndDeviceNames() {

        assertNull(BaresipOption.AUDIO_PLAYER.validate("alsa,default"));
        assertNull(BaresipOption.AUDIO_PLAYER.validate("pulse"));
        assertNull(BaresipOption.AUDIO_SOURCE.validate("pipewire,alsa_input.pci-0_1f.3.analog-stereo"));
        assertNull(BaresipOption.AUDIO_ALERT.validate("default"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "alsa,default\nrm -rf /", "alsa,default;evil"})
    void deviceRejectsBlankAndInjectionChars(String raw) {

        assertNotNull(BaresipOption.AUDIO_PLAYER.validate(raw), "must reject: [" + raw + "]");
    }

    @Test
    void videoCodecAcceptsOnlyListedValues() {

        assertNull(BaresipOption.VIDEO_CODEC.validate("h264"));
        assertNull(BaresipOption.VIDEO_CODEC.validate("vp8"));
        assertNull(BaresipOption.VIDEO_CODEC.validate("h265"));
        assertNotNull(BaresipOption.VIDEO_CODEC.validate("av1"));
        assertNotNull(BaresipOption.VIDEO_CODEC.validate("H264"));
    }

    @Test
    void videoResolutionAcceptsOnlyListedValues() {

        assertNull(BaresipOption.VIDEO_RESOLUTION.validate("176x144"));
        assertNull(BaresipOption.VIDEO_RESOLUTION.validate("320x240"));
        assertNull(BaresipOption.VIDEO_RESOLUTION.validate("640x480"));
        assertNull(BaresipOption.VIDEO_RESOLUTION.validate("1280x720"));
        assertNull(BaresipOption.VIDEO_RESOLUTION.validate("1920x1080"));
        assertNotNull(BaresipOption.VIDEO_RESOLUTION.validate("800x600"));
        assertNotNull(BaresipOption.VIDEO_RESOLUTION.validate("1280x720p"));
    }

    @Test
    void videoBitrateAcceptsRangeAndRejectsOutside() {

        assertNull(BaresipOption.VIDEO_BITRATE.validate("64"));
        assertNull(BaresipOption.VIDEO_BITRATE.validate("1024"));
        assertNull(BaresipOption.VIDEO_BITRATE.validate("16384"));
        assertNotNull(BaresipOption.VIDEO_BITRATE.validate("63"));
        assertNotNull(BaresipOption.VIDEO_BITRATE.validate("16385"));
        assertNotNull(BaresipOption.VIDEO_BITRATE.validate("abc"));
    }

    @Test
    void videoFpsAcceptsRangeAndRejectsOutside() {

        assertNull(BaresipOption.VIDEO_FPS.validate("1"));
        assertNull(BaresipOption.VIDEO_FPS.validate("30"));
        assertNull(BaresipOption.VIDEO_FPS.validate("60"));
        assertNotNull(BaresipOption.VIDEO_FPS.validate("0"));
        assertNotNull(BaresipOption.VIDEO_FPS.validate("61"));
        assertNotNull(BaresipOption.VIDEO_FPS.validate("fast"));
    }

    @Test
    void validationErrorsMentionTheConfigKey() {

        String error = BaresipOption.CALL_MAX_CALLS.validate("-1");

        assertNotNull(error);
        assertTrue(error.contains("call_max_calls"), "error message must identify the option");
    }
}
