package com.jsoftsip.ui.dialog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the open per-call video dialogs keyed by the call id so
 * a second video request for the same call focuses the existing
 * window instead of stacking another one.
 * Mirrors the {@link DialerWindowManager} lifecycle: registration
 * wires the removal on window hidden, and the launcher closes
 * everything during shutdown.
 */
public final class VideoDialogManager {

    private static final Map<String, WindowHandle> OPEN_DIALOGS = new HashMap<>();

    private VideoDialogManager() {
    }

    public static boolean isOpen(String callId) {

        return OPEN_DIALOGS.containsKey(callId);
    }

    public static void register(String callId, WindowHandle handle) {

        OPEN_DIALOGS.put(callId, handle);

        // addEventHandler (exercised via the handle) does not
        // overwrite handlers attached elsewhere, unlike setOnHidden
        // which silently clobbers them.
        handle.addWindowHiddenHandler(() -> OPEN_DIALOGS.remove(callId));
    }

    public static void focus(String callId) {

        WindowHandle handle = OPEN_DIALOGS.get(callId);

        if (handle instanceof StageHandle stageHandle) {

            stageHandle.focus();
        }
    }

    /**
     * Closes every open video dialog and clears the map.
     * A copy is iterated because close fires the window hidden
     * handler, which removes the entry.
     */
    public static void closeAll() {

        List.copyOf(OPEN_DIALOGS.values()).forEach(WindowHandle::close);

        OPEN_DIALOGS.clear();
    }
}
