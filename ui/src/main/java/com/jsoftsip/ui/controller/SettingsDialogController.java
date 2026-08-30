package com.jsoftsip.ui.controller;

import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.settings.baresip.BaresipOption;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.Language;
import com.jsoftsip.ui.ThemeManager;
import com.jsoftsip.ui.ThemeType;
import com.jsoftsip.ui.UiPreferencesService;
import com.jsoftsip.ui.baresip.BaresipSettingsApplier;
import com.jsoftsip.ui.baresip.BaresipSettingsApplier.Outcome;
import com.jsoftsip.ui.baresip.BaresipSettingsFormModel;
import com.jsoftsip.ui.dialog.DialogService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class SettingsDialogController {

    // Quiet time after the last edit before the preview regenerates
    private static final double PREVIEW_DEBOUNCE_MILLIS = 300;

    @FXML
    private TabPane tabPane;

    @FXML
    private ComboBox<ThemeType> cmbTheme;

    @FXML
    private ComboBox<Language> cmbLanguage;

    @FXML
    private CheckBox chkRememberWindowGeometry;

    @FXML
    private CheckBox chkConfirmExitWithCalls;

    @FXML
    private CheckBox chkSaveBaresipLogToFile;

    @FXML
    private Tab tabAudio;

    @FXML
    private Tab tabBaresip;

    @FXML
    private ComboBox<String> cmbAudioPlayer;

    @FXML
    private ComboBox<String> cmbAudioSource;

    @FXML
    private ComboBox<String> cmbAudioAlert;

    @FXML
    private Spinner<Integer> spnCallLocalTimeout;

    @FXML
    private Spinner<Integer> spnCallMaxCalls;

    @FXML
    private TextField txtRtpTimeout;

    @FXML
    private Spinner<Integer> spnRegistrationTimeout;

    @FXML
    private TextField txtAudioBuffer;

    @FXML
    private ComboBox<String> cmbAudioBufferMode;

    @FXML
    private TextField txtAudioSilence;

    @FXML
    private TextField txtAudioJitterBufferMs;

    @FXML
    private ComboBox<String> cmbAudioJitterBufferType;

    @FXML
    private Spinner<Integer> spnAudioJitterBufferSize;

    @FXML
    private CheckBox chkCallAccept;

    @FXML
    private TextField txtSipListen;

    @FXML
    private Tab tabPreview;

    @FXML
    private TextArea txtPreview;

    @FXML
    private Tab tabVideo;

    @FXML
    private ComboBox<String> cmbVideoCodec;

    @FXML
    private ComboBox<String> cmbVideoResolution;

    @FXML
    private TextField txtVideoBitrate;

    @FXML
    private Spinner<Integer> spnVideoFps;

    @FXML
    private Label lblError;

    @FXML
    private HBox busyBox;

    @FXML
    private Button btnApply;

    @FXML
    private Button btnClose;

    @FXML
    private Button btnClearHistory;

    @FXML
    private Button btnRotateMasterKey;

    private final AppContext context;

    private final UiPreferencesService uiPreferencesService;

    // null on the MOCK backend, where no baresip process exists
    private final BaresipSettingsFacade baresipFacade;

    private final BaresipSettingsApplier applier;

    // null whenever baresipFacade is null
    private BaresipSettingsFormModel formModel;

    // language captured when the dialog opens, used to decide whether
    // applying the changes should also close the dialog
    private Language initialLanguage;

    // true when the language selected at apply time differs from the one
    // that was active when the dialog was opened
    private boolean appliedLanguageChanged;

    private final ExecutorService uiExecutor;

    // Base config lines read once per dialog open: the live
    // preview patches over this snapshot instead of re-reading
    // the file on every keystroke
    private volatile List<String> baseConfigLines;

    // Monotonic guard so a stale preview computed by the
    // virtual-thread executor can never overwrite a newer one
    private final AtomicInteger previewGeneration = new AtomicInteger();

    // Coalesces keystroke storms into a single preview generation
    // after typing pauses, created lazily on the FX thread
    private PauseTransition previewDebounce;

    public SettingsDialogController(AppContext context) {

        this.context = context;

        this.uiExecutor = context.getUiExecutor();

        this.uiPreferencesService = context.getUiPreferencesService();

        this.baresipFacade = context.getBaresipSettingsFacade().orElse(null);

        // Nullable on the MOCK backend: the applier itself tolerates
        // a null facade and returns NO_BARESIP, but when the facade is
        // absent there is no form to validate either, so save()
        // short-circuits before reaching the applier.
        this.applier = new BaresipSettingsApplier(baresipFacade, context.getCallService());
    }

    @FXML
    private void initialize() {

        cmbTheme.getItems().setAll(ThemeType.values());

        cmbTheme.setValue(uiPreferencesService.getTheme());

        cmbLanguage.getItems().setAll(Language.values());

        cmbLanguage.setValue(uiPreferencesService.getLanguage());

        initialLanguage = uiPreferencesService.getLanguage();

        cmbLanguage.setConverter(new StringConverter<>() {

            @Override
            public String toString(Language language) {

                return language == null ? "" : I18n.get("language." + language.name().toLowerCase());
            }

            @Override
            public Language fromString(String string) {

                return null;
            }
        });

        // App-level checkboxes stay visible and persist on
        // every backend, MOCK included: only the file-logging
        // one has no effect there because no baresip exists
        chkRememberWindowGeometry.setSelected(uiPreferencesService.isRememberWindowGeometry());

        chkConfirmExitWithCalls.setSelected(uiPreferencesService.isConfirmExitWithCalls());

        chkSaveBaresipLogToFile.setSelected(uiPreferencesService.isSaveBaresipLogToFile());

        btnApply.setOnAction(event -> save());

        btnClose.setOnAction(event -> close());

        IconFactory.configureI18nSuccessButton(btnApply, BootstrapIcons.CHECK, "settings.save.tooltip");

        IconFactory.configureI18nButton(btnClose, BootstrapIcons.X, "settings.close.tooltip");

        IconFactory.configureI18nDangerButton(btnClearHistory, BootstrapIcons.TRASH, "settings.history.clear");

        IconFactory.configureI18nDangerButton(btnRotateMasterKey, BootstrapIcons.ARROW_REPEAT,
                                              "settings.masterkey.rotate");

        I18n.bind(tabPane.getTabs().get(0).textProperty(), "settings.tab.general");

        I18n.bind(tabAudio.textProperty(), "settings.tab.audio");

        I18n.bind(tabBaresip.textProperty(), "settings.tab.baresip");

        I18n.bind(tabVideo.textProperty(), "settings.tab.video");

        I18n.bind(tabPreview.textProperty(), "settings.tab.baresip.config");

        btnClearHistory.setOnAction(event -> clearCallHistory());

        btnRotateMasterKey.setOnAction(event -> rotateMasterKey());

        if (baresipFacade == null) {
            hideBaresipSection();
        } else {
            initBaresipSection();
        }
    }

    /**
     * MOCK backend: no native process exists to reconfigure, so
     * the baresip tabs and the preview are removed entirely
     * instead of showing disabled or misleading controls.
     */
    private void hideBaresipSection() {

        tabPane.getTabs().removeAll(tabAudio, tabBaresip, tabVideo, tabPreview);
    }

    /**
     * Device listing shells out to pactl, so it must never run
     * inside FXMLLoader.load() on the FX thread. One background
     * task on the ui executor fetches sinks once plus sources,
     * and both results are published back through
     * Platform.runLater. While loading, the combos show an i18n
     * placeholder prompt so the dialog opens instantly.
     */
    private void loadDevicesAsync() {

        String loadingPrompt = I18n.get("settings.audio.loading.devices");

        cmbAudioPlayer.setPromptText(loadingPrompt);

        cmbAudioAlert.setPromptText(loadingPrompt);

        cmbAudioSource.setPromptText(loadingPrompt);

        context.getUiExecutor().execute(() -> {

            List<String> sinks = baresipFacade.listSinks();

            List<String> sources = baresipFacade.listSources();

            Platform.runLater(() -> {

                if (isDialogClosed()) {
                    return;
                }

                // one pactl round trip feeds both output combos
                populateDeviceCombo(cmbAudioPlayer, sinks);

                populateDeviceCombo(cmbAudioAlert, sinks);

                populateDeviceCombo(cmbAudioSource, sources);

                cmbAudioPlayer.setPromptText(null);

                cmbAudioAlert.setPromptText(null);

                cmbAudioSource.setPromptText(null);
            });
        });
    }

    /**
     * The listing may finish after the user already closed the
     * dialog. A hidden window is the reliable closed signal,
     * because closing keeps the scene attached. A missing scene
     * or window only means the dialog is not attached yet, so
     * it must not suppress population.
     */
    private boolean isDialogClosed() {

        Scene scene = tabPane.getScene();

        if (scene == null) {
            return false;
        }

        Window window = scene.getWindow();

        return window != null && !window.isShowing();
    }

    private void initBaresipSection() {

        formModel = new BaresipSettingsFormModel(context.getSettingsService()::getSetting);

        loadDevicesAsync();

        loadBaseConfigAsync();

        cmbAudioBufferMode.getItems().setAll("fixed", "adaptive");

        cmbAudioJitterBufferType.getItems().setAll("off", "fixed", "adaptive");

        spnCallLocalTimeout.setValueFactory(intRangeFactory(0, 600));

        spnCallMaxCalls.setValueFactory(intRangeFactory(0, 64));

        spnRegistrationTimeout.setValueFactory(intRangeFactory(5, 120));

        spnAudioJitterBufferSize.setValueFactory(intRangeFactory(1, 1000));

        cmbVideoCodec.getItems().setAll("h264", "vp8", "h265");

        cmbVideoResolution.getItems().setAll("176x144", "320x240", "640x480", "1280x720", "1920x1080");

        spnVideoFps.setValueFactory(intRangeFactory(1, 60));

        loadModelIntoControls();

        // every edit re-validates and schedules the in-memory
        // preview, so the user sees the exact config that a save
        // would produce before committing to a restart
        wireChangeListeners();

        validateAndToggleSave();
    }

    /**
     * The live preview patches over a snapshot taken once when
     * the dialog opens: re-reading the config file on every
     * keystroke put disk access inside the FX thread. Queued after
     * the device fetch so the
     * deterministic queued-executor tests observe the same task
     * order as before.
     */
    private void loadBaseConfigAsync() {

        uiExecutor.execute(() -> {

            List<String> lines = baresipFacade.readBaseConfigLines();

            Platform.runLater(() -> {

                baseConfigLines = lines;

                schedulePreviewRefresh();
            });
        });
    }

    /**
     * Items come from pactl. When pactl is unavailable the
     * list is null and the editable combo degrades to a plain
     * free text field.
     */
    private void populateDeviceCombo(ComboBox<String> combo, List<String> devices) {

        if (devices != null) {
            combo.getItems().setAll(devices);
        }
    }

    private SpinnerValueFactory<Integer> intRangeFactory(int min, int max) {

        return new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, min);
    }

    /**
     * Spinner seed values: a corrupt persisted value falls back
     * to the range minimum instead of crashing the dialog.
     */
    private static int parseIntOr(String key, String raw, int fallback) {

        try {

            return Integer.parseInt(raw.trim());

        } catch (RuntimeException exception) {

            JSoftSipLog.warn("Invalid persisted value for " + key + ", using default " + fallback, exception);

            return fallback;
        }
    }

    private void loadModelIntoControls() {

        setDeviceValue(cmbAudioPlayer, BaresipOption.AUDIO_PLAYER);

        setDeviceValue(cmbAudioSource, BaresipOption.AUDIO_SOURCE);

        setDeviceValue(cmbAudioAlert, BaresipOption.AUDIO_ALERT);

        spnCallLocalTimeout.getValueFactory()
                           .setValue(parseIntOr(BaresipOption.CALL_LOCAL_TIMEOUT.settingsKey(),
                                                formModel.valueFor(BaresipOption.CALL_LOCAL_TIMEOUT), 0));

        spnCallMaxCalls.getValueFactory().setValue(parseIntOr(BaresipOption.CALL_MAX_CALLS.settingsKey(),
                                                              formModel.valueFor(BaresipOption.CALL_MAX_CALLS), 0));

        txtRtpTimeout.setText(formModel.valueFor(BaresipOption.RTP_TIMEOUT));

        spnRegistrationTimeout.getValueFactory()
                              .setValue(parseIntOr(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS,
                                                   context.getSettingsService()
                                                          .getSetting(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS)
                                                          .orElse(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS_DEFAULT),
                                                   30));

        txtAudioBuffer.setText(formModel.valueFor(BaresipOption.AUDIO_BUFFER));

        cmbAudioBufferMode.setValue(formModel.valueFor(BaresipOption.AUDIO_BUFFER_MODE));

        txtAudioSilence.setText(formModel.valueFor(BaresipOption.AUDIO_SILENCE));

        txtAudioJitterBufferMs.setText(formModel.valueFor(BaresipOption.AUDIO_JITTER_BUFFER_MS));

        cmbAudioJitterBufferType.setValue(formModel.valueFor(BaresipOption.AUDIO_JITTER_BUFFER_TYPE));

        spnAudioJitterBufferSize.getValueFactory()
                                .setValue(parseIntOr(BaresipOption.AUDIO_JITTER_BUFFER_SIZE.settingsKey(),
                                                     formModel.valueFor(BaresipOption.AUDIO_JITTER_BUFFER_SIZE), 1));

        chkCallAccept.setSelected("yes".equals(formModel.valueFor(BaresipOption.CALL_ACCEPT)));

        txtSipListen.setText(formModel.valueFor(BaresipOption.SIP_LISTEN));

        cmbVideoCodec.setValue(formModel.valueFor(BaresipOption.VIDEO_CODEC));

        cmbVideoResolution.setValue(formModel.valueFor(BaresipOption.VIDEO_RESOLUTION));

        txtVideoBitrate.setText(formModel.valueFor(BaresipOption.VIDEO_BITRATE));

        spnVideoFps.getValueFactory().setValue(parseIntOr(BaresipOption.VIDEO_FPS.settingsKey(),
                                                          formModel.valueFor(BaresipOption.VIDEO_FPS), 30));
    }

    private void setDeviceValue(ComboBox<String> combo, BaresipOption option) {

        // editable combo: assigning the editor text also drives
        // the committed value once the user confirms it
        combo.getEditor().setText(formModel.valueFor(option));

        combo.setValue(formModel.valueFor(option));
    }

    private void wireChangeListeners() {

        cmbAudioPlayer.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioPlayer.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioSource.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioSource.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioAlert.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioAlert.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        spnCallLocalTimeout.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        spnCallMaxCalls.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtRtpTimeout.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        spnRegistrationTimeout.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtAudioBuffer.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioBufferMode.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtAudioSilence.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtAudioJitterBufferMs.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbAudioJitterBufferType.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        spnAudioJitterBufferSize.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        chkCallAccept.selectedProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtSipListen.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbVideoCodec.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        cmbVideoResolution.valueProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        txtVideoBitrate.textProperty().addListener((obs, oldV, newV) -> onFieldEdited());

        spnVideoFps.getEditor().textProperty().addListener((obs, oldV, newV) -> onFieldEdited());
    }

    private void onFieldEdited() {

        syncModelFromControls();
        validateAndToggleSave();
        schedulePreviewRefresh();
    }

    private void syncModelFromControls() {

        if (formModel == null) {
            return;
        }

        formModel.setValue(BaresipOption.AUDIO_PLAYER, cmbAudioPlayer.getEditor().getText());

        formModel.setValue(BaresipOption.AUDIO_SOURCE, cmbAudioSource.getEditor().getText());

        formModel.setValue(BaresipOption.AUDIO_ALERT, cmbAudioAlert.getEditor().getText());

        formModel.setValue(BaresipOption.CALL_LOCAL_TIMEOUT, spnCallLocalTimeout.getEditor().getText());

        formModel.setValue(BaresipOption.CALL_MAX_CALLS, spnCallMaxCalls.getEditor().getText());

        formModel.setValue(BaresipOption.RTP_TIMEOUT, txtRtpTimeout.getText());

        formModel.setValue(BaresipOption.AUDIO_BUFFER, txtAudioBuffer.getText());

        formModel.setValue(BaresipOption.AUDIO_BUFFER_MODE, cmbAudioBufferMode.getValue());

        formModel.setValue(BaresipOption.AUDIO_SILENCE, txtAudioSilence.getText());

        formModel.setValue(BaresipOption.AUDIO_JITTER_BUFFER_MS, txtAudioJitterBufferMs.getText());

        formModel.setValue(BaresipOption.AUDIO_JITTER_BUFFER_TYPE, cmbAudioJitterBufferType.getValue());

        formModel.setValue(BaresipOption.AUDIO_JITTER_BUFFER_SIZE, spnAudioJitterBufferSize.getEditor().getText());

        formModel.setValue(BaresipOption.CALL_ACCEPT, chkCallAccept.isSelected() ? "yes" : "no");

        formModel.setValue(BaresipOption.SIP_LISTEN, txtSipListen.getText());

        formModel.setValue(BaresipOption.VIDEO_CODEC, cmbVideoCodec.getValue());

        formModel.setValue(BaresipOption.VIDEO_RESOLUTION, cmbVideoResolution.getValue());

        formModel.setValue(BaresipOption.VIDEO_BITRATE, txtVideoBitrate.getText());

        formModel.setValue(BaresipOption.VIDEO_FPS, spnVideoFps.getEditor().getText());
    }

    private void validateAndToggleSave() {

        if (formModel == null) {
            return;
        }

        Map<BaresipOption, String> errors = formModel.validationErrors();

        if (errors.isEmpty()) {

            lblError.setVisible(false);
            lblError.setManaged(false);
            btnApply.setDisable(false);

            return;
        }

        String firstError = errors.values().iterator().next();

        lblError.setText(I18n.format("settings.invalid.value", firstError));
        lblError.setVisible(true);
        lblError.setManaged(true);

        btnApply.setDisable(true);
    }

    /**
     * Debounces the live preview: rapid edits coalesce into one
     * generation once typing pauses, instead of running the
     * patch pipeline per keystroke.
     */
    private void schedulePreviewRefresh() {

        if (previewDebounce == null) {

            previewDebounce = new PauseTransition(Duration.millis(PREVIEW_DEBOUNCE_MILLIS));

            previewDebounce.setOnFinished(event -> generatePreviewAsync());
        }

        previewDebounce.playFromStart();
    }

    /**
     * Preview is generated off the FX thread over the cached
     * base lines: the facade call is pure computation here, but
     * it still runs on the ui executor so no config work ever
     * competes with rendering. The result
     * publishes only if no newer generation was scheduled.
     */
    private void generatePreviewAsync() {

        if (baresipFacade == null || formModel == null) {
            return;
        }

        List<String> base = baseConfigLines;

        if (base == null) {
            return;
        }

        Map<String, String> pending = formModel.toSettingsMap();

        int generation = previewGeneration.incrementAndGet();

        uiExecutor.execute(() -> {

            String text;

            try {

                text = baresipFacade.previewPatchedConfig(pending, base);

            } catch (RuntimeException exception) {

                // Surface a generic message to the user and log the full
                // cause internally so secrets and stack traces stay out of
                // the UI.
                JSoftSipLog.warn("Preview generation failed", exception);

                text = null;
            }

            String resolved = text;

            Platform.runLater(() -> {

                if (previewGeneration.get() != generation) {
                    return;
                }

                txtPreview.setText(resolved != null ? resolved : I18n.get("settings.preview.unavailable"));
            });
        });
    }

    private void save() {

        applyTheme();

        // app-level checkboxes persist straight to the
        // settings table, exactly like the theme: they are
        // NOT baresip options, so the apply-with-restart
        // flow below never sees them
        saveGeneralPreferences();

        appliedLanguageChanged = cmbLanguage.getValue() != null && !cmbLanguage.getValue().equals(initialLanguage);

        saveRegistrationTimeout();

        // MOCK backend: no baresip process to reconfigure.
        if (baresipFacade == null || formModel == null) {
            if (appliedLanguageChanged) {
                closeAndNotifyLanguageChanged();
            }
            return;
        }

        syncModelFromControls();

        if (!formModel.isValid()) {

            validateAndToggleSave();

            return;
        }

        boolean restartApproved = confirmRestartWithActiveCalls();

        if (!restartApproved) {
            return;
        }

        applyBaresipSettingsAsync(restartApproved);
    }

    /**
     * The restart drops every active call, so the user must
     * explicitly accept that before the apply starts.
     */
    private boolean confirmRestartWithActiveCalls() {

        if (context.getCallService().getActiveCalls().isEmpty()) {

            return true;
        }

        return DialogService.confirm(null, I18n.get("settings.restart.title"), I18n.get("settings.restart.header"),
                                     I18n.get("settings.restart.content"));
    }

    /**
     * apply() blocks while baresip restarts (up to the ctrl_tcp
     * wait window), so it runs on a background thread and the
     * outcome is published back on the JavaFX thread.
     */
    private void applyBaresipSettingsAsync(boolean restartApproved) {

        setBusy(true);

        Thread.ofVirtual().start(() -> {

            Outcome outcome = applier.apply(formModel, restartApproved);

            Platform.runLater(() -> {

                setBusy(false);

                publishApplyOutcome(outcome);
            });
        });
    }

    private void setBusy(boolean busy) {

        busyBox.setVisible(busy);
        busyBox.setManaged(busy);

        // the preview lives inside the tab pane now, so
        // disabling the pane covers every tab at once
        tabPane.setDisable(busy);
        btnApply.setDisable(busy);
        btnClose.setDisable(busy);
    }

    private void publishApplyOutcome(Outcome outcome) {

        if (outcome instanceof Outcome.Applied applied) {

            if (appliedLanguageChanged) {
                closeAndNotifyLanguageChanged();
                return;
            }

            showApplyResult(applied.result());

            return;
        }

        if (outcome instanceof Outcome.Invalid invalid) {

            JSoftSipLog.warn("Skipping baresip apply: invalid form: " + invalid.errors().keySet());

            validateAndToggleSave();

            return;
        }

        // NoBaresip and RestartCancelled are safe no-ops: the
        // controller already gated NoBaresip, and RestartCancelled
        // was already confirmed before applyBaresipSettingsAsync
        // was called, so neither publishes an alert.
    }

    private void showApplyResult(BaresipSettingsFacade.ApplyResult result) {

        switch (result) {

            case APPLIED -> DialogService.showInfo(null, I18n.get("settings.apply.success.title"), null,
                                                   I18n.get("settings.apply.success"));

            case RESTORED_BACKUP -> DialogService.showInfo(null, I18n.get("settings.apply.restored.header"), null,
                                                           I18n.get("settings.apply.restored.content"));

            case FAILED -> DialogService.showError(null, I18n.get("settings.apply.failed.title"),
                                                   I18n.get("settings.apply.failed.header"),
                                                   I18n.get("settings.apply.failed.content"));
        }
    }

    private void applyTheme() {

        ThemeType theme = cmbTheme.getValue();

        ThemeManager.applyTheme(theme);

        uiPreferencesService.saveTheme(theme);
    }

    private void saveGeneralPreferences() {

        uiPreferencesService.saveRememberWindowGeometry(chkRememberWindowGeometry.isSelected());

        uiPreferencesService.saveConfirmExitWithCalls(chkConfirmExitWithCalls.isSelected());

        uiPreferencesService.saveBaresipLogToFile(chkSaveBaresipLogToFile.isSelected());

        Language language = cmbLanguage.getValue();

        if (language != null) {

            if (language != uiPreferencesService.getLanguage()) {
                I18n.setLocale(language.getLocale());
            }

            uiPreferencesService.saveLanguage(language);
        }
    }

    private void saveRegistrationTimeout() {

        String value = spnRegistrationTimeout.getEditor().getText();

        if (value == null || value.isBlank()) {
            value = SettingsKeys.REGISTRATION_TIMEOUT_SECONDS_DEFAULT;
        }

        context.getSettingsService().saveSetting(SettingsKeys.REGISTRATION_TIMEOUT_SECONDS, value.trim());
    }

    private void close() {

        Stage stage = (Stage) btnClose.getScene().getWindow();

        stage.close();
    }

    /**
     * Informs the user that the language was applied and then closes the
     * dialog.
     * <p>
     * The alert is shown while the settings dialog is still open. That lets
     * JavaFX assign it a valid owner automatically and the DialogPane
     * (content, graphic and buttons) renders correctly. Closing afterwards
     * reveals the main window already in the new language, which is the whole
     * point of auto-closing on a language change. Showing the alert after
     * closing the settings stage instead left it owner-less in the same pulse
     * and it rendered as an empty title bar.
     */
    private void closeAndNotifyLanguageChanged() {

        DialogService.showInfo(null, I18n.get("settings.language.applied.title"), null,
                               I18n.get("settings.language.applied.content"));

        close();
    }

    private void clearCallHistory() {

        boolean accepted = DialogService.confirm(null, I18n.get("history.clear.title"),
                                                 I18n.get("history.clear.header"), I18n.get("history.clear.content"));

        if (!accepted) {
            return;
        }

        context.getHistoryService().clearAll();

        DialogService.showInfo(null, I18n.get("dialog.success.title"), null, I18n.get("history.clear.success"));
    }

    private void rotateMasterKey() {

        boolean accepted = DialogService.confirm(null, I18n.get("settings.masterkey.rotate.title"),
                                                 I18n.get("settings.masterkey.rotate.header"),
                                                 I18n.get("settings.masterkey.rotate.content"));

        if (!accepted) {
            return;
        }

        try {

            context.getAccountService().rotateMasterKey();

            DialogService.showInfo(null, I18n.get("dialog.success.title"), null,
                                   I18n.get("settings.masterkey.rotate.success"));

        } catch (RuntimeException exception) {

            JSoftSipLog.error("Master key rotation failed", exception);

            DialogService.showError(null, I18n.get("settings.masterkey.rotate.failed.header"), null,
                                    I18n.get("settings.masterkey.rotate.failed.content"));
        }
    }
}
