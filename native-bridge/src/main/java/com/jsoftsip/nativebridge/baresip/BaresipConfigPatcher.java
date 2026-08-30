package com.jsoftsip.nativebridge.baresip;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure patch engine for baresip config files. It operates on the
 * config as a list of lines and never touches the filesystem or
 * any process, which keeps it fully unit testable.
 *
 * <p>For each patch, in the order given:
 * <ul>
 *   <li>every active line with the same key is replaced in
 *       place, so hand edited duplicate keys cannot survive
 *       with a later conflicting value</li>
 *   <li>a commented line with the same key is replaced in
 *       place, which uncomments it</li>
 *   <li>when no line matches, the patch is appended at the
 *       end, preceded by its comment line when one is set</li>
 * </ul>
 *
 * <p>Replaced lines are written as "key<TAB>value". Applying the
 * same patch list twice yields the same result, so callers can
 * re-patch a regenerated config on every launch. The immutable
 * audio-safety patch (call_hold_other_calls no) is NOT hardcoded
 * here: the caller appends it as the last patch, which keeps this
 * engine generic.
 */
public final class BaresipConfigPatcher {

    private BaresipConfigPatcher() {
    }

    /**
     * Applies the patches to base and returns the result as a new
     * list. The input list is never modified.
     */
    public static List<String> apply(List<String> base, List<ConfigPatch> patches) {

        List<String> result = new ArrayList<>(base);

        for (ConfigPatch patch : patches) {
            applyOne(result, patch);
        }

        return List.copyOf(result);
    }

    private static void applyOne(List<String> lines, ConfigPatch patch) {

        Pattern keyLine = Pattern.compile("^\\s*#?\\s*" + Pattern.quote(patch.key()) + "\\s+.*$");

        boolean replaced = false;

        for (int index = 0; index < lines.size(); index++) {

            if (keyLine.matcher(lines.get(index)).matches()) {

                lines.set(index, render(patch));

                replaced = true;
            }
        }

        if (replaced) {
            return;
        }

        if (patch.comment() != null) {
            lines.add("# " + patch.comment());
        }

        lines.add(render(patch));
    }

    private static String render(ConfigPatch patch) {

        return patch.key() + "\t" + patch.value();
    }
}
