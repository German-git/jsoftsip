package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallSession;
import com.jsoftsip.core.call.CallSessionListener;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.util.AccountIdentityFormatter;
import com.jsoftsip.ui.dialog.DialogService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class DialerDialogController {

    @FXML
    private Label lblAccount;

    @FXML
    private TextField txtDestination;

    @FXML
    private Button btnCall;

    @FXML
    private Button btnHangup;

    @FXML
    private Button btnStar;

    @FXML
    private Button btnHash;

    @FXML
    private Button btnDelete;

    @FXML
    private Button btn0;

    @FXML
    private Button btn1;

    @FXML
    private Button btn2;

    @FXML
    private Button btn3;

    @FXML
    private Button btn4;

    @FXML
    private Button btn5;

    @FXML
    private Button btn6;

    @FXML
    private Button btn7;

    @FXML
    private Button btn8;

    @FXML
    private Button btn9;

    private SipAccount account;

    // JavaFX mirror of the active session's "active" flag. Controls are
    // bound to it so they reset automatically when the session ends, even
    // on a remote-end termination that never reached a local CallListener.
    private final BooleanProperty sessionActive = new SimpleBooleanProperty(false);

    private CallSession activeSession;

    private CallSessionListener sessionListener;

    private final AccountService accountService;

    private final CallService callService;

    private final ExecutorService uiExecutor;

    public DialerDialogController(AppContext context) {

        this.accountService = context.getAccountService();

        this.callService = context.getCallService();

        this.uiExecutor = context.getUiExecutor();
    }

    @FXML
    private void initialize() {

        btnCall.setOnAction(event -> startCall());

        btnHangup.setOnAction(event -> hangup());

        IconFactory.configureI18nPositiveButton(btnCall, BootstrapIcons.TELEPHONE_OUTBOUND_FILL,
                                                "dialer.start.tooltip");

        IconFactory.configureI18nDangerButton(btnHangup, BootstrapIcons.TELEPHONE_X_FILL, "dialer.hangup.tooltip");

        txtDestination.promptTextProperty().bind(I18n.createStringBinding("dialer.prompt"));

        configureDialPad();
        // TODO
        //  Future:
        //  When a call is active, the dial pad should
        //  send DTMF tones instead of modifying the
        //  destination field.

        // Controls reflect the active session through a reactive binding:
        // while a session is active the call controls are disabled and the
        // destination field is locked, when the session ends they reset on
        // their own.
        btnCall.disableProperty().bind(sessionActive);
        btnHangup.disableProperty().bind(sessionActive.not());
        txtDestination.disableProperty().bind(sessionActive);
    }

    public void setAccount(SipAccount account) {

        this.account = account;

        lblAccount.setText(AccountIdentityFormatter.formatInline(account));
    }

    private void startCall() {

        String destination = txtDestination.getText();

        if (destination == null || destination.isBlank()) {

            return;
        }

        boolean isOnline = accountService.findById(account.getId()).map(a -> a.getStatus() == AccountStatus.ONLINE)
                                         .orElse(false);

        if (!isOnline) {

            DialogService.showWarning(null, I18n.get("dialer.account.offline.title"), null,
                                      I18n.get("dialer.account.offline.message"));

            return;
        }

        uiExecutor.execute(() -> {

            try {

                CallLeg call = (CallLeg) callService.startCall(account, destination);

                Platform.runLater(() -> handleCallStarted(call));

            } catch (Exception exception) {

                Platform.runLater(() -> handleCallFailed(exception));
            }
        });
    }

    private void handleCallStarted(CallLeg call) {

        CallSession session = call.getSession();

        // Without a session there is nothing to observe, keep the controls
        // enabled rather than leaving the dialog wedged in a disabled state.
        if (session == null) {

            return;
        }

        // Drop any observer from a previous call before tracking the new one.
        detachSessionObserver();

        activeSession = session;
        sessionActive.set(session.isActive());

        sessionListener = changed -> Platform.runLater(() -> sessionActive.set(changed.isActive()));
        session.addSessionListener(sessionListener);
    }

    private void handleCallFailed(Throwable exception) {

        JSoftSipLog.error("Failed to start call", exception);

        DialogService.showError(null, I18n.get("dialer.call.error.title"), null, I18n.get("dialer.call.error.message"));
    }

    private void hangup() {

        if (activeSession == null) {
            return;
        }

        // End the live leg that belongs to this account within the session.
        // Stale ENDED legs must be skipped, or a redial that reused the
        // session would hang up the dead leg and silently no-op. The session
        // aggregate ends any partner leg and the binding resets the controls
        // once the session becomes inactive.
        Optional<CallLeg> matchedLeg = activeSession.getLegs().stream().filter(leg -> leg.getAccount() != null
            && leg.getState() != CallState.ENDED && Objects.equals(leg.getAccount().getId(), account.getId()))
                                                    .findFirst();

        if (matchedLeg.isPresent()) {
            callService.endCall(matchedLeg.get().getBackendCallId());
            return;
        }

        // Fallback: if no leg matched by account id (e.g. sessionKey
        // normalization differences), end the first live leg of the session.
        Optional<CallLeg> firstLiveLeg = activeSession.getLegs().stream()
                                                      .filter(leg -> leg.getState() != CallState.ENDED).findFirst();

        if (firstLiveLeg.isEmpty()) {
            return;
        }

        callService.endCall(firstLiveLeg.get().getBackendCallId());
    }

    private void detachSessionObserver() {

        if (activeSession != null && sessionListener != null) {
            activeSession.removeSessionListener(sessionListener);
        }

        activeSession = null;
        sessionListener = null;
    }

    private void close() {

        dispose();

        Stage stage = (Stage) btnCall.getScene().getWindow();

        stage.close();
    }

    public void dispose() {

        detachSessionObserver();
    }

    private void configureDialPad() {

        configureDigitButton(btn0);
        configureDigitButton(btn1);
        configureDigitButton(btn2);
        configureDigitButton(btn3);
        configureDigitButton(btn4);
        configureDigitButton(btn5);
        configureDigitButton(btn6);
        configureDigitButton(btn7);
        configureDigitButton(btn8);
        configureDigitButton(btn9);

        configureDigitButton(btnStar);
        configureDigitButton(btnHash);

        btnDelete.setOnAction(event -> deleteCharacter());
    }

    private void configureDigitButton(Button button) {

        button.setOnAction(event -> appendText(button.getText()));
    }

    private void appendText(String text) {

        txtDestination.appendText(text);

        txtDestination.requestFocus();

        txtDestination.positionCaret(txtDestination.getText().length());
    }

    private void deleteCharacter() {

        String text = txtDestination.getText();

        if (text.isEmpty()) {
            return;
        }

        txtDestination.setText(text.substring(0, text.length() - 1));

        txtDestination.positionCaret(txtDestination.getText().length());
    }
}
