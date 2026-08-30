package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.registration.RegistrationListener;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipRegistrationState;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.SelectedAccountContext;
import com.jsoftsip.ui.dialog.AccountDialog;
import com.jsoftsip.ui.dialog.DialogService;
import com.jsoftsip.ui.model.AccountListItem;
import com.jsoftsip.ui.util.AccountIdentityFormatter;
import com.jsoftsip.ui.util.RegistrationFailurePresentation;
import com.jsoftsip.ui.util.SipAccountDiff;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class AccountsPaneController {

    @FXML
    private ListView<AccountListItem> accountsListView;

    @FXML
    private Button btnAdd;

    @FXML
    private Button btnEdit;

    @FXML
    private Button btnDelete;

    @FXML
    private TitledPane accountsTitle;

    private final AccountService accountService;

    private final RegistrationService registrationService;

    private final SelectedAccountContext selectedAccountContext;

    private final ExecutorService uiExecutor;

    private final Set<Long> pendingRegistrations = new HashSet<>();

    public AccountsPaneController(AppContext context) {

        this.accountService = context.getAccountService();

        this.registrationService = context.getRegistrationService();

        this.selectedAccountContext = context.getSelectedAccountContext();

        this.uiExecutor = context.getUiExecutor();
    }

    private final AccountStatusListener accountStatusListener = accountId -> Platform.runLater(() -> updateAccountItem(accountId));

    private final RegistrationListener registrationListener = event -> Platform.runLater(() -> {
        handleRegistrationEvent(event);
        updateAccountItem(event.getAccountId());
    });

    @FXML
    private void initialize() {

        btnAdd.setOnAction(event -> onAdd());

        btnEdit.setOnAction(event -> onEdit());

        btnDelete.setOnAction(event -> onDelete());

        accountService.addListener(accountStatusListener);

        registrationService.addRegistrationListener(registrationListener);

        refreshAccounts();

        configureContextMenu();

        accountsListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {

            if (newValue == null) {

                selectedAccountContext.setSelectedAccount(null);

                return;
            }

            selectedAccountContext.setSelectedAccount(newValue.getAccount());
        });

        IconFactory.configureI18nButton(btnAdd, BootstrapIcons.PLUS, "accounts.add");

        IconFactory.configureI18nButton(btnEdit, BootstrapIcons.PENCIL_FILL, "accounts.edit");

        IconFactory.configureI18nDangerButton(btnDelete, BootstrapIcons.TRASH, "accounts.delete");

        I18n.bind(accountsTitle.textProperty(), "accounts.title");

        configureCellFactory();
    }

    private void configureCellFactory() {

        accountsListView.setCellFactory(listView -> new ListCell<AccountListItem>() {

            private Timeline animation;

            @Override
            protected void updateItem(AccountListItem item, boolean empty) {

                super.updateItem(item, empty);

                stopAnimation();

                if (empty || item == null) {

                    setGraphic(null);

                    setText(null);

                    return;
                }

                setText(null);

                HBox row = createRow(item);
                setGraphic(row);

                Region dot = (Region) row.lookup(".account-status-dot");

                if (dot != null && isRegistering(item.getAccount().getId())) {

                    animation = createRegistrationAnimation(dot);
                    animation.play();
                }
            }

            private void stopAnimation() {

                if (animation != null) {

                    animation.stop();
                    animation = null;
                }
            }
        });
    }

    private HBox createRow(AccountListItem item) {

        SipAccount account = item.getAccount();

        Region dot = new Region();

        dot.getStyleClass().add("account-status-dot");

        boolean online = account.getStatus() == AccountStatus.ONLINE;

        dot.getStyleClass().add(online ? "account-status-online" : "account-status-offline");

        Label nameLabel = new Label(AccountIdentityFormatter.formatInline(account));

        HBox row = new HBox(6, dot, nameLabel);

        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    private void onAdd() {

        AccountDialog.showCreateDialog().ifPresent(account -> {

            account.setStatus(com.jsoftsip.core.account.AccountStatus.OFFLINE);

            accountService.createAccount(account);

            refreshAccounts();
        });
    }

    private void onEdit() {

        AccountListItem selectedItem = accountsListView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {

            showInformation(I18n.get("accounts.select.first"));

            return;
        }

        SipAccount account = selectedItem.getAccount();

        // The edit dialog mutates the pre-edit instance in
        // place, so the previous SIP fields must be captured
        // before the dialog opens.
        SipAccount previousSipState = SipAccountDiff.snapshotSipFields(account);

        AccountDialog.showEditDialog(account).ifPresent(updatedAccount -> {

            accountService.updateAccount(updatedAccount);

            if (SipAccountDiff.hasSipFieldChanges(previousSipState, updatedAccount)) {
                registrationService.reprovisionAccount(updatedAccount);
            }

            refreshAccounts();
        });
    }

    private void onDelete() {

        AccountListItem selectedItem = accountsListView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {

            showInformation(I18n.get("accounts.select.first"));

            return;
        }

        boolean confirmed = DialogService.confirm(null, I18n.get("accounts.delete.title"),
                                                  I18n.get("accounts.delete.header"),
                                                  I18n.get("accounts.delete.content"));

        if (!confirmed) {

            return;
        }

        registrationService.unregisterAccount(selectedItem.getAccount().getId());

        accountService.deleteAccount(selectedItem.getAccount().getId());

        refreshAccounts();
    }

    private void updateAccountItem(long accountId) {

        // The DB lookup stays off the FX thread:
        // only the list mutation publishes back through runLater
        uiExecutor.execute(() -> {

            Optional<SipAccount> updated = accountService.findById(accountId);

            Platform.runLater(() -> updated.ifPresent(this::applyAccountUpdate));
        });
    }

    /**
     * Applies an updated account to its list item. Must run on the
     * FX thread.
     */
    private void applyAccountUpdate(SipAccount updatedAccount) {

        long accountId = updatedAccount.getId();

        for (int i = 0; i < accountsListView.getItems().size(); i++) {

            AccountListItem item = accountsListView.getItems().get(i);

            if (item.getAccount().getId() != accountId) {
                continue;
            }

            boolean wasSelected = accountsListView.getSelectionModel().getSelectedItem() == item;

            AccountListItem newItem = new AccountListItem(updatedAccount);
            accountsListView.getItems().set(i, newItem);

            if (wasSelected) {
                accountsListView.getSelectionModel().select(newItem);
            }

            return;
        }
    }

    private void refreshAccounts() {

        // Capture the selection on the FX thread, then fetch and
        // render off it: the SQLite read never runs inside the FX
        // event
        AccountListItem selectedItem = accountsListView.getSelectionModel().getSelectedItem();

        Long selectedAccountId = selectedItem != null ? selectedItem.getAccount().getId() : null;

        uiExecutor.execute(() -> {

            List<SipAccount> accounts = accountService.getAccounts();

            Platform.runLater(() -> renderAccounts(accounts, selectedAccountId));
        });
    }

    /**
     * Renders the given accounts preserving the previously selected
     * account id. Must run on the FX thread.
     */
    private void renderAccounts(List<SipAccount> accounts, Long selectedAccountId) {

        accountsListView.getItems().clear();

        accounts.forEach(account -> accountsListView.getItems().add(new AccountListItem(account)));

        if (selectedAccountId == null) {

            return;
        }

        accountsListView.getItems().stream().filter(item -> item.getAccount().getId().equals(selectedAccountId))
                        .findFirst().ifPresent(item -> accountsListView.getSelectionModel().select(item));
    }

    private void configureContextMenu() {

        MenuItem registerItem = new MenuItem();

        // Bound, not set: the menu is long-lived on the main
        // window, so the items must re-translate when the locale
        // changes
        registerItem.textProperty().bind(I18n.createStringBinding("accounts.register"));

        MenuItem unregisterItem = new MenuItem();

        unregisterItem.textProperty().bind(I18n.createStringBinding("accounts.unregister"));

        registerItem.setOnAction(event -> registerSelectedAccount());

        unregisterItem.setOnAction(event -> unregisterSelectedAccount());

        ContextMenu contextMenu = new ContextMenu(registerItem, unregisterItem);

        contextMenu.setOnShowing(event -> {

            AccountListItem selected = accountsListView.getSelectionModel().getSelectedItem();

            if (selected == null) {
                registerItem.setDisable(true);
                unregisterItem.setDisable(true);
                return;
            }

            com.jsoftsip.core.account.AccountStatus status = selected.getAccount().getStatus();

            long accountId = selected.getAccount().getId();

            registerItem.setDisable(status == com.jsoftsip.core.account.AccountStatus.ONLINE
                || status == com.jsoftsip.core.account.AccountStatus.UNAVAILABLE
                || pendingRegistrations.contains(accountId));

            unregisterItem.setDisable(status == com.jsoftsip.core.account.AccountStatus.OFFLINE);
        });

        accountsListView.setContextMenu(contextMenu);
    }

    private void registerSelectedAccount() {

        AccountListItem selectedItem = accountsListView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            return;
        }

        long accountId = selectedItem.getAccount().getId();

        if (pendingRegistrations.contains(accountId)) {
            return;
        }

        pendingRegistrations.add(accountId);
        accountsListView.refresh();

        registrationService.registerAccount(selectedItem.getAccount());
    }

    boolean isRegistering(long accountId) {

        return pendingRegistrations.contains(accountId);
    }

    private Timeline createRegistrationAnimation(Region dot) {

        Timeline timeline = new Timeline(new KeyFrame(Duration.ZERO, event -> setRegisteringStyle(dot, true)),
            new KeyFrame(Duration.millis(300), event -> setRegisteringStyle(dot, false)),
            new KeyFrame(Duration.millis(600), event -> setRegisteringStyle(dot, true)));

        timeline.setCycleCount(Timeline.INDEFINITE);

        return timeline;
    }

    private void setRegisteringStyle(Region dot, boolean registering) {

        dot.getStyleClass().remove("account-status-online");
        dot.getStyleClass().remove("account-status-offline");
        dot.getStyleClass().remove("account-status-registering");

        dot.getStyleClass().add(registering ? "account-status-registering" : "account-status-offline");
    }

    private void unregisterSelectedAccount() {

        AccountListItem selectedItem = accountsListView.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            return;
        }

        registrationService.unregisterAccount(selectedItem.getAccount().getId());
    }

    private void showInformation(String message) {

        DialogService.showInfo(null, I18n.get("dialog.info.title"), null, message);
    }

    /**
     * Runs on the JavaFX thread. A failed registration is
     * surfaced as a non-modal error alert, so the user keeps
     * control of the window while the app reports the problem.
     */
    private void handleRegistrationEvent(SipRegistrationEvent event) {

        SipRegistrationState state = event.getState();

        if (state == SipRegistrationState.REGISTERED || state == SipRegistrationState.UNREGISTERED
            || state == SipRegistrationState.FAILED) {

            pendingRegistrations.remove(event.getAccountId());
            accountsListView.refresh();
        }

        if (state != SipRegistrationState.FAILED) {
            return;
        }

        DialogService.showError(null, I18n.get("accounts.registration.failed.title"), null,
                                RegistrationFailurePresentation.forEvent(event).message());
    }
}
