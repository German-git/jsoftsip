package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.ControllerFactory;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.SceneFactory;
import com.jsoftsip.ui.controller.DialerDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class DialerDialog {

    private DialerDialog() {
    }

    public static void open(AppContext context, SipAccount account) {

        try {

            FXMLLoader loader = new FXMLLoader(DialerDialog.class.getResource("/fxml/DialerDialog.fxml"));

            loader.setResources(I18n.bundle());
            loader.setControllerFactory(new ControllerFactory(context));

            Scene scene = SceneFactory.create(loader.load());

            DialerDialogController controller = loader.getController();

            controller.setAccount(account);

            Stage stage = new Stage();

            // Bound so a locale change re-translates the title of
            // this long-lived window
            stage.titleProperty().bind(I18n.formatBinding("dialer.title", account.getDisplayName()));

            stage.setScene(scene);

            WindowHandle handle = new StageHandle(stage);

            // Attached before register() on purpose: both handlers
            // are addEventHandler-based and must survive the manager
            // registration, so close() later runs dispose() too.
            handle.addWindowHiddenHandler(() -> controller.dispose());

            DialerWindowManager.register(account.getId(), handle);

            stage.show();

        } catch (Exception exception) {

            throw new RuntimeException("Failed to open dialer", exception);
        }
    }
}
