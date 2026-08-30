package com.jsoftsip.ui.window;

import javafx.stage.Stage;

import java.util.Optional;

/**
 * Holds a reference to the primary application stage so modal
 * dialogs opened from anywhere can declare it as their owner.
 * The launcher registers the stage right
 * after building its scene, until then callers must tolerate an
 * absent owner and fall back to unowned modality.
 */
public final class MainWindowRegistry {

    private static volatile Stage mainWindow;

    private MainWindowRegistry() {
    }

    public static void register(Stage stage) {

        mainWindow = stage;
    }

    public static Optional<Stage> mainWindow() {

        return Optional.ofNullable(mainWindow);
    }
}
