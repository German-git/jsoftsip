package com.jsoftsip.ui.dialog;

import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.ControllerFactory;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.SceneFactory;
import com.jsoftsip.ui.window.ModalWindowTracker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class SettingsDialog {

    private SettingsDialog() {
    }

    public static void show(AppContext context) {

        try {

            FXMLLoader loader = new FXMLLoader(SettingsDialog.class.getResource("/fxml/SettingsDialog.fxml"));

            loader.setResources(I18n.bundle());
            loader.setControllerFactory(new ControllerFactory(context));

            Scene scene = SceneFactory.create(loader.load());

            Stage stage = new Stage();

            stage.initModality(Modality.APPLICATION_MODAL);

            ModalWindowTracker.register(stage);

            stage.setOnHidden(event -> ModalWindowTracker.remove(stage));

            // Bound so a locale change re-translates the title
            stage.titleProperty().bind(I18n.createStringBinding("settings.dialog.title"));

            stage.setScene(scene);

            // default window size, the user may resize it freely
            stage.setWidth(580);
            stage.setHeight(560);

            stage.setResizable(true);

            stage.showAndWait();

        } catch (Exception exception) {

            throw new RuntimeException("Failed to open settings dialog", exception);
        }
    }
}