package com.jsoftsip.ui.window;

import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the application-modal dialog stages so the launcher
 * can close them before the primary window closes. Dialogs
 * register their stage after creation and remove it on
 * onHidden. All calls run on the FX thread.
 */
public final class ModalWindowTracker {

    private static final List<Stage> OPEN_MODALS = new ArrayList<>();

    private ModalWindowTracker() {
    }

    public static void register(Stage stage) {

        OPEN_MODALS.add(stage);
    }

    public static void remove(Stage stage) {

        OPEN_MODALS.remove(stage);
    }

    /**
     * Closes every tracked stage and clears the list.
     * Already-hidden stages are skipped. A copy is iterated
     * because close fires onHidden, which may remove stages.
     */
    public static void closeAll() {

        List.copyOf(OPEN_MODALS).forEach(stage -> {

            if (stage != null && stage.isShowing()) {

                stage.close();
            }
        });

        OPEN_MODALS.clear();
    }
}