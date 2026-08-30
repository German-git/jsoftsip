package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.dialog.DialerDialog;
import com.jsoftsip.ui.dialog.DialerWindowManager;
import com.jsoftsip.ui.dialog.DialogService;
import com.jsoftsip.ui.dialog.SettingsDialog;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.util.Duration;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

public class TopToolbarController {

    @FXML
    private Button btnSettings;

    @FXML
    private Button btnDialer;

    @FXML
    private Slider volumeSlider;

    @FXML
    private Label volumeIcon;

    @FXML
    private Label microphoneIcon;

    @FXML
    private Slider microphoneSlider;

    private final AppContext context;

    private PauseTransition volumeDebounce;

    private PauseTransition microphoneDebounce;

    public TopToolbarController(AppContext context) {

        this.context = context;
    }

    @FXML
    private void initialize() {

        int savedOutputVolume = loadVolume(SettingsKeys.VOLUME_OUTPUT, SettingsKeys.VOLUME_OUTPUT_DEFAULT);

        int savedMicrophoneVolume = loadVolume(SettingsKeys.VOLUME_MICROPHONE, SettingsKeys.VOLUME_MICROPHONE_DEFAULT);

        volumeSlider.setMin(0);
        volumeSlider.setMax(100);
        volumeSlider.setBlockIncrement(5);
        volumeSlider.setMajorTickUnit(5);
        volumeSlider.setSnapToTicks(true);
        volumeSlider.setValue(savedOutputVolume);

        volumeDebounce = new PauseTransition(Duration.millis(100));
        volumeDebounce.setOnFinished(event -> {

            int volume = (int) Math.round(volumeSlider.getValue());

            context.getUiExecutor().execute(() -> context.getCallService().setVolume(volume));

            persistVolume(SettingsKeys.VOLUME_OUTPUT, volume);
        });

        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> volumeDebounce.playFromStart());

        microphoneSlider.setMin(0);
        microphoneSlider.setMax(100);
        microphoneSlider.setBlockIncrement(5);
        microphoneSlider.setMajorTickUnit(5);
        microphoneSlider.setSnapToTicks(true);
        microphoneSlider.setValue(savedMicrophoneVolume);

        microphoneDebounce = new PauseTransition(Duration.millis(100));
        microphoneDebounce.setOnFinished(event -> {

            int volume = (int) Math.round(microphoneSlider.getValue());

            context.getUiExecutor().execute(() -> context.getCallService().setMicrophoneVolume(volume));

            persistVolume(SettingsKeys.VOLUME_MICROPHONE, volume);
        });

        microphoneSlider.valueProperty()
                        .addListener((observable, oldValue, newValue) -> microphoneDebounce.playFromStart());

        // Apply the persisted values once at startup: the slider
        // listeners are attached after setValue, so a value loaded
        // from the DB would never reach the SIP client otherwise
        // (and would be lost on the next CALL_ESTABLISHED re-apply).
        context.getUiExecutor().execute(() -> context.getCallService().setVolume(savedOutputVolume));

        context.getUiExecutor().execute(() -> context.getCallService().setMicrophoneVolume(savedMicrophoneVolume));

        btnSettings.setOnAction(event -> SettingsDialog.show(context));

        btnDialer.setOnAction(event -> openDialer());

        IconFactory.configureI18nButton(btnSettings, BootstrapIcons.GEAR_FILL, "toolbar.settings.tooltip");

        IconFactory.configureI18nPositiveButton(btnDialer, BootstrapIcons.PHONE_FILL, "toolbar.dialer.tooltip");

        IconFactory.configureI18nLabel(volumeIcon, BootstrapIcons.SPEAKER_FILL, "toolbar.volume.output");

        IconFactory.configureI18nLabel(microphoneIcon, BootstrapIcons.MIC_FILL, "toolbar.volume.microphone");

    }

    private void openDialer() {

        SipAccount account = context.getSelectedAccountContext().getSelectedAccount();

        if (account == null) {

            DialogService.showInfo(null, I18n.get("toolbar.account.select.title"), null,
                                   I18n.get("toolbar.account.select"));

            return;
        }

        if (account.getStatus() != AccountStatus.ONLINE) {

            DialogService.showError(null, I18n.get("toolbar.account.offline.title"), null,
                                    I18n.format("toolbar.account.offline", account.getDisplayName()));

            return;
        }

        if (DialerWindowManager.isOpen(account.getId())) {

            DialerWindowManager.focus(account.getId());

            return;
        }

        DialerDialog.open(context, account);
    }

    private int loadVolume(String key, String defaultValue) {

        String raw = context.getSettingsService().getSetting(key).orElse(defaultValue);

        try {

            return Integer.parseInt(raw);

        } catch (NumberFormatException exception) {

            JSoftSipLog.warn("Invalid persisted value for " + key + ", using default", exception);

            return Integer.parseInt(defaultValue);
        }
    }

    private void persistVolume(String key, int value) {

        try {

            context.getSettingsService().saveSetting(key, String.valueOf(value));

        } catch (RuntimeException exception) {

            // A persistence failure must never break the UI
            // (the slider still works for the current session).
            JSoftSipLog.warn("Failed to persist setting: " + key, exception);
        }
    }
}
