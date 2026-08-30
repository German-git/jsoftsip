package com.jsoftsip.launcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import com.jsoftsip.ui.I18n;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 * Undecorated splash window shown while the application context is built
 * on a background thread. The main stage remains hidden until the startup
 * task completes, so the JavaFX thread never blocks on the baresip
 * ctrl_tcp wait window.
 */
final class StartupSplash {

    private static final double SPLASH_WIDTH = 320;

    private static final double SPLASH_HEIGHT = 220;

    private final Stage stage;

    private final Label messageLabel;

    StartupSplash() {

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: #252631;");

        ImageView logo = new ImageView(
            new Image(StartupSplash.class.getResourceAsStream("/icons/jsoftsip-icon-64px.png")));
        logo.setFitWidth(64);
        logo.setFitHeight(64);
        logo.setPreserveRatio(true);

        Label title = new Label("JSoftSip");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        messageLabel = new Label(I18n.get("startup.message"));
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e0e0e0;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(32, 32);

        root.getChildren().addAll(logo, title, messageLabel, progress);

        Scene scene = new Scene(root, SPLASH_WIDTH, SPLASH_HEIGHT);

        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setTitle("JSoftSip");
        stage.setResizable(false);

        // Closing the splash while the context
        // is still being constructed used to call Platform.exit(),
        // racing stop() against the constructor so a fully built
        // context (and its baresip child process) could survive
        // with no shutdown. The splash stays unclosable until
        // showMainStage or handleStartupFailure closes it
        // programmatically - stage.close() bypasses this handler.
        stage.setOnCloseRequest(WindowEvent::consume);
    }

    void show() {

        stage.centerOnScreen();
        stage.show();
    }

    void close() {

        stage.close();
    }
}
