package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaresipPatchPlanTest {

    private InMemorySettings settings;

    private StubDeviceLister lister;

    @BeforeEach
    void setUp() {

        settings = new InMemorySettings();

        lister = new StubDeviceLister();
    }

    @Test
    void overrideBeatsDatabaseAndDefault() {

        settings.saveSetting("baresip.call_max_calls", "4");

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister, Map.of("baresip.call_max_calls", "8"));

        assertEquals("8", patchValue(patches, "call_max_calls"));
    }

    @Test
    void databaseBeatsRegistryDefault() {

        settings.saveSetting("baresip.call_max_calls", "4");

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister, Map.of());

        assertEquals("4", patchValue(patches, "call_max_calls"));
    }

    @Test
    void unsetOptionFallsBackToRegistryDefault() {

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister, Map.of());

        assertEquals(BaresipOption.CALL_LOCAL_TIMEOUT.defaultValue(), patchValue(patches, "call_local_timeout"));
    }

    @Test
    void emptyValueNormalizesToZero() {

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister, Map.of("baresip.rtp_timeout", ""));

        assertEquals("0", patchValue(patches, "rtp_timeout"),
                     "an empty rtp timeout means disabled and baresip" + " disables the timeout with 0");
    }

    @Test
    void forcedHoldNoIsAlwaysTheLastPatch() {

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister, Map.of("baresip.call_max_calls", "8"));

        ConfigPatch last = patches.get(patches.size() - 1);

        assertEquals("call_hold_other_calls", last.key());
        assertEquals("no", last.value());
    }

    @Test
    void vanishedDeviceFallsBackToRegistryDefault() {

        lister.sinks = List.of("sink_a");

        List<ConfigPatch> patches = BaresipPatchPlan.patches(settings, lister,
                                                             Map.of("baresip.audio_player", "alsa,gone_sink"));

        assertEquals(BaresipOption.AUDIO_PLAYER.defaultValue(), patchValue(patches, "audio_player"),
                     "a device missing from the fresh pactl listing must" + " fall back to the template default");
    }

    private static String patchValue(List<ConfigPatch> patches, String key) {

        for (ConfigPatch patch : patches) {

            if (patch.key().equals(key)) {
                return patch.value();
            }
        }

        return null;
    }

    /**
     * In-memory SettingsService fake: no database, just a map.
     */
    private static final class InMemorySettings implements SettingsService {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public void saveSetting(String key, String value) {

            values.put(key, value);
        }

        @Override
        public void deleteSetting(String key) {

            values.remove(key);
        }

        @Override
        public Optional<String> getSetting(String key) {

            return Optional.ofNullable(values.get(key));
        }
    }

    /**
     * Device lister stub: returns scripted sink/source lists,
     * null meaning pactl is unavailable.
     */
    private static final class StubDeviceLister extends PactlDeviceLister {

        private List<String> sinks = List.of();

        private List<String> sources = List.of();

        @Override
        public List<String> listSinks() {

            return sinks;
        }

        @Override
        public List<String> listSources() {

            return sources;
        }
    }
}
