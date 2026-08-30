package com.jsoftsip.ui.dialog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DialerWindowManager {

    private static final Map<Long, WindowHandle> OPEN_WINDOWS = new HashMap<>();

    private DialerWindowManager() {
    }

    public static boolean isOpen(long accountId) {

        return OPEN_WINDOWS.containsKey(accountId);
    }

    public static void register(long accountId, WindowHandle handle) {

        OPEN_WINDOWS.put(accountId, handle);

        // addEventHandler (exercised via the handle) does not
        // overwrite handlers attached elsewhere, unlike setOnHidden
        // which silently clobbers them and leaks the dialer's
        // dispose callback.
        handle.addWindowHiddenHandler(() -> OPEN_WINDOWS.remove(accountId));
    }

    public static void focus(long accountId) {

        WindowHandle handle = OPEN_WINDOWS.get(accountId);

        if (handle instanceof StageHandle stageHandle) {

            stageHandle.focus();
        }
    }

    /**
     * Closes every open dialer window and clears the map.
     * A copy is iterated because close fires the window hidden
     * handler, which removes the entry.
     */
    public static void closeAll() {

        List.copyOf(OPEN_WINDOWS.values()).forEach(WindowHandle::close);

        OPEN_WINDOWS.clear();
    }
}
