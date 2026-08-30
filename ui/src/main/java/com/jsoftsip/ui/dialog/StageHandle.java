package com.jsoftsip.ui.dialog;

import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Real {@link WindowHandle} backed by a JavaFX {@link Stage}.
 */
public final class StageHandle implements WindowHandle {

    private final Stage stage;

    public StageHandle(Stage stage) {

        this.stage = stage;
    }

    @Override
    public void addWindowHiddenHandler(Runnable handler) {

        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> handler.run());
    }

    @Override
    public void close() {

        stage.close();
    }

    public void focus() {

        stage.toFront();

        stage.requestFocus();
    }
}