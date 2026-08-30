package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.dialog.DialogService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

public class AccountDialogController {

    @FXML
    private TextField txtDisplayName;

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtDomain;

    @FXML
    private ComboBox<SipTransport> cmbTransport;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    private SipAccount result;

    @FXML
    private void initialize() {

        IconFactory.configureI18nSuccessButton(btnSave, BootstrapIcons.CHECK, "account.save");

        IconFactory.configureI18nButton(btnCancel, BootstrapIcons.X, "account.cancel");

        cmbTransport.getItems().setAll(SipTransport.values());

        cmbTransport.getSelectionModel().select(SipTransport.UDP);

        btnSave.setOnAction(event -> save());

        btnCancel.setOnAction(event -> close());
    }

    private void save() {

        SipAccount account = result != null ? result : new SipAccount();

        account.setDisplayName(txtDisplayName.getText());

        account.setUsername(trimmed(txtUsername.getText()));

        account.setPassword(txtPassword.getText());

        account.setDomain(trimmed(txtDomain.getText()));

        account.setTransport(cmbTransport.getValue());

        if (!AccountInputValidator.isValid(account)) {

            showValidationError();

            return;
        }

        if (account.getStatus() == null) {

            account.setStatus(AccountStatus.OFFLINE);
        }

        result = account;

        close();
    }

    private void close() {

        Stage stage = (Stage) btnCancel.getScene().getWindow();

        stage.close();
    }

    public SipAccount getResult() {
        return result;
    }

    public void setAccount(SipAccount account) {

        this.result = account;

        txtDisplayName.setText(account.getDisplayName());

        txtUsername.setText(account.getUsername());

        txtPassword.setText(account.getPassword());

        txtDomain.setText(account.getDomain());

        cmbTransport.setValue(account.getTransport());
    }

    private static String trimmed(String value) {

        return value == null ? "" : value.trim();
    }

    private static void showValidationError() {

        DialogService.showError(null, I18n.get("account.invalid.title"), I18n.get("account.invalid.header"),
                                I18n.get("account.invalid.content"));
    }
}
