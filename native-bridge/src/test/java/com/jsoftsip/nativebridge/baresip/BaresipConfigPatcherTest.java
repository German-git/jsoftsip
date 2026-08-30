package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipConfigPatcherTest {

    private static final ConfigPatch HOLD_NO = new ConfigPatch("call_hold_other_calls", "no",
        "Forced by JSoftSIP: audio for concurrent calls");

    @Test
    void replacesActiveLineInPlace() {

        List<String> base = List.of("call_max_calls 2", "rtp_stats\tno");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("call_max_calls", "8", null)));

        assertEquals(2, result.size());
        assertEquals("call_max_calls\t8", result.get(0));
        assertEquals("rtp_stats\tno", result.get(1));
    }

    @Test
    void dbPatchWinsOverUserConfigValue() {

        List<String> base = List.of("call_max_calls 2");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("call_max_calls", "8", null)));

        assertEquals(List.of("call_max_calls\t8"), result);
        assertFalse(result.stream().anyMatch(line -> line.contains(" 2")), "old value must be gone");
    }

    @Test
    void reapplyIsIdempotent() {

        List<String> base = List.of("#rtp_timeout 60", "call_max_calls 2");

        List<ConfigPatch> patches = List.of(new ConfigPatch("rtp_timeout", "30", null),
                                            new ConfigPatch("call_max_calls", "8", null), HOLD_NO);

        List<String> once = BaresipConfigPatcher.apply(base, patches);
        List<String> twice = BaresipConfigPatcher.apply(once, patches);

        assertEquals(once, twice, "second apply must not change anything");

        long holdLines = twice.stream().filter(line -> line.matches("^call_hold_other_calls\\s.*")).count();

        assertEquals(1, holdLines, "no duplicate lines allowed");
    }

    @Test
    void uncommentAndReplaceCommentedLine() {

        List<String> base = List.of("# AVT settings", "#rtp_timeout 60", "rtp_stats\tno");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("rtp_timeout", "30", null)));

        assertEquals("rtp_timeout\t30", result.get(1));
        assertEquals(3, result.size(), "no line may be added");
        assertFalse(result.stream().anyMatch(line -> line.startsWith("#rtp_timeout")),
                    "commented line must be uncommented");
    }

    @Test
    void appendsAbsentKeyWithCommentBlock() {

        List<String> base = List.of("rtp_stats\tno");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(HOLD_NO));

        assertEquals(3, result.size());
        assertEquals("rtp_stats\tno", result.get(0));
        assertEquals("# Forced by JSoftSIP: audio for concurrent calls", result.get(1));
        assertEquals("call_hold_other_calls\tno", result.get(2));
    }

    @Test
    void appendsAbsentKeyWithoutCommentWhenNull() {

        List<String> result = BaresipConfigPatcher.apply(List.of(),
                                                         List.of(new ConfigPatch("rtp_timeout", "30", null)));

        assertEquals(List.of("rtp_timeout\t30"), result);
    }

    @Test
    void forcedHoldNoAppliedLastWinsOverYes() {

        List<String> base = List.of("call_hold_other_calls yes");

        List<ConfigPatch> patches = List.of(new ConfigPatch("call_max_calls", "8", null), HOLD_NO);

        List<String> result = BaresipConfigPatcher.apply(base, patches);

        assertTrue(result.contains("call_hold_other_calls\tno"), "forced patch must win over user config value");
        assertFalse(result.stream().anyMatch(line -> line.matches("^call_hold_other_calls\\s+yes.*")),
                    "yes must not survive");
    }

    @Test
    void minimalFallbackInputEndsWithHoldNo() {

        List<String> minimal = List.of("# Minimal JSoftSIP config: ctrl_tcp only", "module_app\t\tctrl_tcp.so");

        List<ConfigPatch> patches = List.of(new ConfigPatch("call_local_timeout", "120", null), HOLD_NO);

        List<String> result = BaresipConfigPatcher.apply(minimal, patches);

        assertEquals("call_hold_other_calls\tno", result.get(result.size() - 1),
                     "forced hold patch must be the last config line");
        assertTrue(result.contains("call_local_timeout\t120"));
    }

    @Test
    void keyPrefixDoesNotMatchLongerKeys() {

        List<String> base = List.of("audio_buffer_mode fixed");

        List<String> result = BaresipConfigPatcher.apply(base,
                                                         List.of(new ConfigPatch("audio_buffer", "20-160", null)));

        assertEquals("audio_buffer_mode fixed", result.get(0));
        assertEquals("audio_buffer\t20-160", result.get(1));
    }

    @Test
    void patchesApplyInGivenOrderLastWins() {

        List<String> base = List.of("call_max_calls 2");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("call_max_calls", "8", null),
                                                                       new ConfigPatch("call_max_calls", "6", null)));

        assertEquals(List.of("call_max_calls\t6"), result);
    }

    @Test
    void duplicateKeyLinesAreAllReplaced() {

        List<String> base = List.of("# hand edited config with duplicates", "call_hold_other_calls yes",
                                    "rtp_stats\tno", "call_hold_other_calls yes");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(HOLD_NO));

        assertFalse(result.stream().anyMatch(line -> line.matches("^call_hold_other_calls\\s+yes.*")),
                    "no duplicate line may survive with a later yes");

        long holdLines = result.stream().filter(line -> line.equals("call_hold_other_calls\tno")).count();

        assertTrue(holdLines >= 1, "every occurrence must be replaced with the patched value");
        assertTrue(result.contains("rtp_stats\tno"), "unrelated lines must survive");
    }

    @Test
    void duplicateCommentedAndActiveLinesAreAllReplaced() {

        List<String> base = List.of("#rtp_timeout 60", "rtp_timeout 90");

        List<String> result = BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("rtp_timeout", "30", null)));

        assertEquals(2, result.size(), "no line may be added");
        assertFalse(result.stream().anyMatch(line -> line.matches("^\\s*#?\\s*rtp_timeout\\s+(60|90).*")),
                    "both the commented and the active duplicate must be replaced");
        assertTrue(result.stream().allMatch(line -> !line.contains("rtp_timeout") || line.equals("rtp_timeout\t30")),
                   "every rtp_timeout line must hold the patched value");
    }

    @Test
    void inputListIsNotMutated() {

        List<String> base = new ArrayList<>(List.of("call_max_calls 2"));

        BaresipConfigPatcher.apply(base, List.of(new ConfigPatch("call_max_calls", "8", null)));

        assertEquals(List.of("call_max_calls 2"), base);
    }
}
