package com.jsoftsip.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

public final class UiLoader {

    private UiLoader() {
    }

    public static Parent loadMainView(AppContext context) throws IOException {

        FXMLLoader loader = new FXMLLoader(UiLoader.class.getResource("/fxml/MainView.fxml"));

        loader.setResources(I18n.bundle());
        loader.setControllerFactory(new ControllerFactory(context));

        return loader.load();
    }
}
