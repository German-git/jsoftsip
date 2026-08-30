package com.jsoftsip.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class SceneFactory {

    private SceneFactory() {
    }

    public static Scene create(Parent root) {

        Scene scene = new Scene(root);

        ThemeManager.getApplicationStylesheet().ifPresent(stylesheet -> scene.getStylesheets().add(stylesheet));

        return scene;
    }
}