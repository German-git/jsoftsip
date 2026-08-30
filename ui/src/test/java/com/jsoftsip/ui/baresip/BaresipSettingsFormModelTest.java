package com.jsoftsip.ui.baresip;

import com.jsoftsip.core.settings.baresip.BaresipOption;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipSettingsFormModelTest {

    private static BaresipSettingsFormModel modelWith(Map<String, String> persisted) {

        return new BaresipSettingsFormModel(key -> Optional.ofNullable(persisted.get(key)));
    }

    @Test
    void defaultsAreUsedWhenNothingIsPersisted() {

        BaresipSettingsFormModel model = modelWith(Map.of());

        for (BaresipOption option : BaresipOption.values()) {

            assertEquals(option.defaultValue(), model.valueFor(option), option.configKey());
        }
    }

    @Test
    void persistedValueOverridesDefault() {

        BaresipSettingsFormModel model = modelWith(Map.of(BaresipOption.RTP_TIMEOUT.settingsKey(), "30"));

        assertEquals("30", model.valueFor(BaresipOption.RTP_TIMEOUT));

        assertEquals(BaresipOption.CALL_MAX_CALLS.defaultValue(), model.valueFor(BaresipOption.CALL_MAX_CALLS));
    }

    @Test
    void defaultsProduceNoValidationErrors() {

        BaresipSettingsFormModel model = modelWith(Map.of());

        assertTrue(model.validationErrors().isEmpty());
        assertTrue(model.isValid());
    }

    @Test
    void invalidValueProducesNamedError() {

        BaresipSettingsFormModel model = modelWith(Map.of());

        model.setValue(BaresipOption.CALL_MAX_CALLS, "-1");

        assertFalse(model.isValid());

        String error = model.validationErrors().get(BaresipOption.CALL_MAX_CALLS);

        assertTrue(error.contains("call_max_calls"), error);

        // fixing the value clears the error again
        model.setValue(BaresipOption.CALL_MAX_CALLS, "8");

        assertTrue(model.isValid());
        assertNull(model.validationErrors().get(BaresipOption.CALL_MAX_CALLS));
    }

    @Test
    void emptyRtpTimeoutIsAccepted() {

        BaresipSettingsFormModel model = modelWith(Map.of());

        model.setValue(BaresipOption.RTP_TIMEOUT, "");

        assertTrue(model.isValid());

        assertEquals("", model.toSettingsMap().get(BaresipOption.RTP_TIMEOUT.settingsKey()));
    }

    @Test
    void invalidEnumValueIsRejected() {

        BaresipSettingsFormModel model = modelWith(Map.of());

        model.setValue(BaresipOption.AUDIO_BUFFER_MODE, "bogus");

        assertFalse(model.isValid());
        assertTrue(model.validationErrors().containsKey(BaresipOption.AUDIO_BUFFER_MODE));
    }

    @Test
    void settingsMapContainsEveryOptionWithPendingValue() {

        Map<String, String> persisted = new HashMap<>();

        persisted.put(BaresipOption.SIP_LISTEN.settingsKey(), "0.0.0.0:5062");

        BaresipSettingsFormModel model = modelWith(persisted);

        model.setValue(BaresipOption.CALL_LOCAL_TIMEOUT, "90");

        Map<String, String> map = model.toSettingsMap();

        assertEquals(BaresipOption.values().length, map.size());

        for (BaresipOption option : BaresipOption.values()) {

            assertTrue(map.containsKey(option.settingsKey()), option.settingsKey());
        }

        assertEquals("90", map.get(BaresipOption.CALL_LOCAL_TIMEOUT.settingsKey()));

        assertEquals("0.0.0.0:5062", map.get(BaresipOption.SIP_LISTEN.settingsKey()));

        assertEquals(BaresipOption.AUDIO_BUFFER.defaultValue(), map.get(BaresipOption.AUDIO_BUFFER.settingsKey()));
    }

    @Test
    void tiersPartitionTheWholeRegistry() {

        var audio = BaresipSettingsFormModel.audioTier();
        var baresip = BaresipSettingsFormModel.baresipTier();
        var video = BaresipSettingsFormModel.videoTier();

        var union = new HashSet<>(audio);
        union.addAll(baresip);
        union.addAll(video);

        assertEquals(allOptions(), union, "the three tiers must cover the whole registry");

        assertTrue(audio.stream().noneMatch(baresip::contains), "audio and baresip must be disjoint");

        assertTrue(audio.stream().noneMatch(video::contains), "audio and video must be disjoint");

        assertTrue(baresip.stream().noneMatch(video::contains), "baresip and video must be disjoint");

        assertTrue(audio.contains(BaresipOption.AUDIO_PLAYER));
        assertTrue(audio.contains(BaresipOption.AUDIO_SOURCE));
        assertTrue(audio.contains(BaresipOption.AUDIO_ALERT));
        assertTrue(audio.contains(BaresipOption.AUDIO_BUFFER));
        assertTrue(audio.contains(BaresipOption.AUDIO_BUFFER_MODE));
        assertTrue(audio.contains(BaresipOption.AUDIO_SILENCE));
        assertTrue(audio.contains(BaresipOption.AUDIO_JITTER_BUFFER_MS));
        assertTrue(audio.contains(BaresipOption.AUDIO_JITTER_BUFFER_TYPE));
        assertTrue(audio.contains(BaresipOption.AUDIO_JITTER_BUFFER_SIZE));

        assertTrue(baresip.contains(BaresipOption.CALL_LOCAL_TIMEOUT));
        assertTrue(baresip.contains(BaresipOption.CALL_MAX_CALLS));
        assertTrue(baresip.contains(BaresipOption.RTP_TIMEOUT));
        assertTrue(baresip.contains(BaresipOption.SIP_LISTEN));
        assertTrue(baresip.contains(BaresipOption.CALL_ACCEPT));

        // the video options must NOT live in the audio or baresip tier
        assertFalse(audio.contains(BaresipOption.VIDEO_CODEC), "VIDEO_CODEC must live in the video tier");
        assertFalse(audio.contains(BaresipOption.VIDEO_RESOLUTION), "VIDEO_RESOLUTION must live in the video tier");
        assertFalse(audio.contains(BaresipOption.VIDEO_BITRATE), "VIDEO_BITRATE must live in the video tier");
        assertFalse(audio.contains(BaresipOption.VIDEO_FPS), "VIDEO_FPS must live in the video tier");

        assertFalse(baresip.contains(BaresipOption.VIDEO_CODEC), "VIDEO_CODEC must live in the video tier");
        assertFalse(baresip.contains(BaresipOption.VIDEO_RESOLUTION), "VIDEO_RESOLUTION must live in the video tier");
        assertFalse(baresip.contains(BaresipOption.VIDEO_BITRATE), "VIDEO_BITRATE must live in the video tier");
        assertFalse(baresip.contains(BaresipOption.VIDEO_FPS), "VIDEO_FPS must live in the video tier");
    }

    @Test
    void videoTierContainsExactlyTheFourVideoOptions() {

        var video = BaresipSettingsFormModel.videoTier();

        assertEquals(java.util.Set.of(BaresipOption.VIDEO_CODEC, BaresipOption.VIDEO_RESOLUTION,
                                      BaresipOption.VIDEO_BITRATE, BaresipOption.VIDEO_FPS),
                     new HashSet<>(video), "videoTier() must hold exactly the four VIDEO_* options");
    }

    @Test
    void audioTierKeepsExactlyNineAudioOptions() {

        assertEquals(9, BaresipSettingsFormModel.audioTier().size(),
                     "the Audio tab must hold exactly the nine audio options");
    }

    @Test
    void baresipTierKeepsExactlyFiveOptions() {

        assertEquals(5, BaresipSettingsFormModel.baresipTier().size(),
                     "the Baresip tab must hold exactly the five baresip options");
    }

    private static java.util.Set<BaresipOption> allOptions() {

        return new HashSet<>(java.util.List.of(BaresipOption.values()));
    }
}
