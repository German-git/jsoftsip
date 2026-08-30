package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.controller.AccountDialogController;
import com.jsoftsip.ui.window.ModalWindowTracker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

public final class AccountDialog {

    private AccountDialog() {
    }

    public static Optional<SipAccount> showCreateDialog() {

        return showDialog(null);
    }

    public static Optional<SipAccount> showEditDialog(SipAccount account) {

        return showDialog(account);
    }

    private static Optional<SipAccount> showDialog(SipAccount account) {

        try {

            FXMLLoader loader = new FXMLLoader(AccountDialog.class.getResource("/fxml/AccountDialog.fxml"));

            loader.setResources(I18n.bundle());

            Scene scene = com.jsoftsip.ui.SceneFactory.create(loader.load());

            AccountDialogController controller = loader.getController();

            if (account != null) {
                controller.setAccount(account);
            }

            Stage stage = new Stage();

            stage.initModality(Modality.APPLICATION_MODAL);

            ModalWindowTracker.register(stage);

            stage.setOnHidden(event -> ModalWindowTracker.remove(stage));

            // Bound so a locale change re-translates the title
            stage.titleProperty()
                 .bind(I18n.createStringBinding(account == null
                     ? "account.dialog.title.create"
                     : "account.dialog.title.edit"));

            stage.setScene(scene);

            stage.showAndWait();

            return Optional.ofNullable(controller.getResult());

        } catch (Exception exception) {

            throw new RuntimeException("Failed to open account dialog", exception);
        }
    }
}