package com.jsoftsip.ui.dialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The video dialog manager must track one
 * window per call, drop the entry when the window hides and
 * close every tracked window on shutdown.
 */
class VideoDialogManagerTest {

    private final List<Runnable> hiddenHandlers = new ArrayList<>();

    @AfterEach
    void tearDown() {

        VideoDialogManager.closeAll();
    }

    private WindowHandle fakeHandle(boolean[] closed) {

        return new WindowHandle() {

            @Override
            public void addWindowHiddenHandler(Runnable handler) {

                hiddenHandlers.add(handler);
            }

            @Override
            public void close() {

                closed[0] = true;
            }
        };
    }

    @Test
    void registersAndUnregistersThroughTheHiddenHandler() {

        boolean[] closed = {false};

        assertFalse(VideoDialogManager.isOpen("call-1"));

        VideoDialogManager.register("call-1", fakeHandle(closed));

        assertTrue(VideoDialogManager.isOpen("call-1"));

        hiddenHandlers.forEach(Runnable::run);

        assertFalse(VideoDialogManager.isOpen("call-1"), "hiding the window removes the tracking entry");
        assertFalse(closed[0], "hiding is not closing");
    }

    @Test
    void closeAllClosesEveryTrackedDialog() {

        boolean[] first = {false};
        boolean[] second = {false};

        VideoDialogManager.register("call-1", fakeHandle(first));
        VideoDialogManager.register("call-2", fakeHandle(second));

        // The registration handler of call-2 captured the same
        // shared list, clear it so only closeAll's handlers run
        // for this assertion about the close path.
        hiddenHandlers.clear();

        VideoDialogManager.closeAll();

        assertTrue(first[0] && second[0], "closeAll must close both tracked windows");
        assertFalse(VideoDialogManager.isOpen("call-1"));
        assertFalse(VideoDialogManager.isOpen("call-2"));
    }

    @Test
    void focusToleratesNonStageHandles() {

        boolean[] closed = {false};

        VideoDialogManager.register("call-1", fakeHandle(closed));

        assertDoesNotThrow(() -> VideoDialogManager.focus("call-1"),
                           "a plain handle without stage capabilities must be safe to focus");
        assertDoesNotThrow(() -> VideoDialogManager.focus("unknown"), "focusing an unknown call must be a no-op");
    }
}
