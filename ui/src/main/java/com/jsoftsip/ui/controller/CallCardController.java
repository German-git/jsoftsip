package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.util.CallDurationFormatter;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.util.CallDirectionPresentation;
import com.jsoftsip.ui.util.CallHoldSupport;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class CallCardController {

    @FXML
    private Label lblOrigin;

    @FXML
    private Label lblDirection;

    @FXML
    private Label lblDestination;

    @FXML
    private Label lblState;

    @FXML
    private Label lblDuration;

    @FXML
    private Button btnHold;

    @FXML
    private Button btnMute;

    @FXML
    private Button btnHangup;

    @FXML
    private Button btnVideo;

    @FXML
    private Button btnAnswer;

    @FXML
    private Button btnReject;

    private CallLeg call;

    private boolean muteState;

    private boolean videoTxEnabled;

    private Timeline durationTimeline;

    private final CallService callService;

    private final ExecutorService uiExecutor;

    private Consumer<CallLeg> videoOpener;

    public CallCardController(AppContext context) {

        this.callService = context.getCallService();

        this.uiExecutor = context.getUiExecutor();
    }

    @FXML
    private void initialize() {

        btnAnswer.textProperty().bind(I18n.createStringBinding("callcard.answer"));
        btnReject.textProperty().bind(I18n.createStringBinding("callcard.reject"));

        btnHold.setOnAction(event -> toggleHold());

        btnMute.setOnAction(event -> toggleMute());

        btnVideo.setOnAction(event -> toggleVideo());

        btnHangup.setOnAction(event -> {

            if (call == null) {
                return;
            }

            JSoftSipLog.info("Hangup " + call.getBackendCallId());

            callService.endCall(call.getBackendCallId());
        });

        btnAnswer.setOnAction(event -> {

            if (call == null) {
                return;
            }

            JSoftSipLog.info("Answer " + call.getBackendCallId());

            callService.answerCall(call.getBackendCallId());
        });

        btnReject.setOnAction(event -> {

            if (call == null) {
                return;
            }

            JSoftSipLog.info("Reject " + call.getBackendCallId());

            callService.rejectCall(call.getBackendCallId());
        });

        IconFactory.configureI18nDangerButton(btnHangup, BootstrapIcons.TELEPHONE_X_FILL, "callcard.hangup.tooltip");
    }

    public void setCall(CallLeg call) {

        this.call = call;

        muteState = false;

        videoTxEnabled = false;

        refresh();

        startDurationUpdater();
    }

    private void toggleHold() {

        if (call == null) {
            return;
        }

        if (call.isPeerLocalAccount()) {

            // For intra-app calls neither hold nor resume can
            // leave both legs active (baresip flip-flop), so
            // the toggle must never reach the backend.
            return;
        }

        if (call.getState() == CallState.HOLD) {

            JSoftSipLog.info("Resume " + call.getBackendCallId());

            callService.resumeCall(call.getBackendCallId());

        } else {

            JSoftSipLog.info("Hold " + call.getBackendCallId());

            callService.holdCall(call.getBackendCallId());
        }

        refresh();
    }

    private void toggleMute() {

        if (call == null) {
            return;
        }

        muteState = !muteState;

        JSoftSipLog.info("" + (muteState ? "Mute " : "Unmute ") + call.getBackendCallId());

        callService.setMicrophoneMuted(muteState);

        refresh();
    }

    private void toggleVideo() {

        if (call == null || !callService.isVideoSupported()) {
            return;
        }

        boolean target = !videoTxEnabled;

        CallLeg requestedCall = call;

        JSoftSipLog.info("" + (target ? "Start video TX" : "Stop video TX") + " " + call.getBackendCallId());

        // The bridge waits up to videodirTimeoutMs for the
        // backend to acknowledge the command, so the call must
        // never run on the FX thread: a slow or dead ctrl_tcp
        // would freeze the whole UI. The result is published
        // back on the FX thread, mirroring the dialer pattern.
        uiExecutor.execute(() -> {

            try {

                boolean accepted = callService.setVideoTransmissionEnabled(target);

                Platform.runLater(() -> handleVideoToggleResult(requestedCall, target, accepted));

            } catch (Exception exception) {

                JSoftSipLog.error("Failed to toggle video transmission", exception);
            }
        });
    }

    /**
     * Applies an acknowledged video toggle back on the FX
     * thread. Results belonging to a leg that is no longer
     * bound to this card (recycled cell) are dropped so a late
     * answer can never flip the state of another call.
     */
    private void handleVideoToggleResult(CallLeg requestedCall, boolean target, boolean accepted) {

        if (!accepted || call != requestedCall) {
            return;
        }

        videoTxEnabled = target;

        // When TX is being enabled, also surface the in-call
        // video dialog so the user can see the remote feed.
        // The opener is injected by the cell/controller factory
        // layer so this class does not depend on the dialog
        // module directly.
        if (target && videoOpener != null) {

            videoOpener.accept(call);
        }

        refresh();
    }

    /**
     * Registers a callback invoked when the user enables video
     * TX on this card. Typically wired to
     * {@link com.jsoftsip.ui.dialog.VideoCallDialogFactory}
     * by the list-cell layer.
     */
    public void setVideoOpener(Consumer<CallLeg> opener) {

        this.videoOpener = opener;
    }

    private void refresh() {

        if (call == null) {
            return;
        }

        CallDirection direction = call.getDirection();

        if (direction == null) {

            lblDirection.setVisible(false);

            lblDirection.setManaged(false);

        } else {

            CallDirectionPresentation presentation = CallDirectionPresentation.forDirection(direction);

            lblDirection.getStyleClass().removeAll("history-direction", "direction-incoming", "direction-outgoing");

            lblDirection.getStyleClass().addAll("history-direction", presentation.cssClass());

            lblDirection.setText(presentation.glyph());

            lblDirection.setVisible(true);

            lblDirection.setManaged(true);
        }

        lblOrigin.setText(resolveUsername(call.getAccount()));

        lblDestination.setText(call.getDestination());

        lblState.setText(I18n.format("callcard.state",
                                     I18n.get("callcard.state." + call.getState().name().toLowerCase())));

        boolean incoming = call.getState() == CallState.INCOMING;

        btnAnswer.setVisible(incoming);

        btnAnswer.setManaged(incoming);

        btnReject.setVisible(incoming);

        btnReject.setManaged(incoming);

        btnHold.setVisible(!incoming);

        btnHold.setManaged(!incoming);

        boolean peerLocal = call.isPeerLocalAccount();

        // A disabled button receives no mouse events, so a
        // disabled peer-local Hold would never show its
        // tooltip. Keep the button enabled, block the action
        // in toggleHold and render it as disabled instead.
        btnHold.setDisable(!peerLocal && !CallHoldSupport.isHoldAllowed(call));

        if (peerLocal) {

            btnHold.setOpacity(0.4);

            btnHold.setFocusTraversable(false);

            btnHold.setCursor(Cursor.DEFAULT);

        } else {

            btnHold.setOpacity(1.0);

            btnHold.setFocusTraversable(true);

            btnHold.setCursor(Cursor.HAND);
        }

        btnMute.setVisible(!incoming);

        btnMute.setManaged(!incoming);

        btnHangup.setVisible(!incoming);

        btnHangup.setManaged(!incoming);

        btnVideo.setVisible(!incoming && callService.isVideoSupported());

        btnVideo.setManaged(!incoming && callService.isVideoSupported());

        IconFactory.configureI18nButton(btnVideo,
                                        videoTxEnabled
                                            ? BootstrapIcons.CAMERA_VIDEO_FILL
                                            : BootstrapIcons.CAMERA_VIDEO_OFF_FILL,
                                        videoTxEnabled ? "callcard.video.stop" : "callcard.video.start");

        if (peerLocal) {

            IconFactory.configureI18nButton(btnHold, BootstrapIcons.PAUSE_FILL, "callcard.hold.disabled.tooltip");

        } else if (call.getState() == CallState.HOLD) {

            IconFactory.configureI18nButton(btnHold, BootstrapIcons.PLAY_FILL, "callcard.resume");

        } else {

            IconFactory.configureI18nButton(btnHold, BootstrapIcons.PAUSE_FILL, "callcard.hold.tooltip");
        }

        IconFactory.configureI18nButton(btnMute, muteState ? BootstrapIcons.MIC_MUTE_FILL : BootstrapIcons.MIC_FILL,
                                        muteState ? "callcard.unmute" : "callcard.mute");

        updateDuration();
    }

    private void startDurationUpdater() {

        if (durationTimeline != null) {
            durationTimeline.stop();
        }

        durationTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateDuration()));

        durationTimeline.setCycleCount(Timeline.INDEFINITE);

        durationTimeline.play();
    }

    private void updateDuration() {

        if (call == null) {
            return;
        }

        if (call.getState() == CallState.ENDED) {

            if (durationTimeline != null) {
                durationTimeline.stop();
            }
        }

        lblDuration.setText(I18n.format("callcard.duration", CallDurationFormatter.format(call.getDurationSeconds())));
    }

    /**
     * Stops the duration timeline. Called by the list-cell layer
     * when the card is recycled or discarded, so a call's timeline
     * never outlives the cell that owns it.
     */
    public void dispose() {

        if (durationTimeline != null) {

            durationTimeline.stop();
        }
    }

    /**
     * Resolves the local identity shown on the card, mirroring
     * VideoCallDialogController.resolveAccountIdentity: a null
     * account no longer throws NPE (REQ-2 guard).
     */
    private static String resolveUsername(SipAccount account) {

        if (account == null) {

            return "";
        }

        String displayName = account.getDisplayName();

        if (displayName != null && !displayName.isBlank()) {

            return displayName;
        }

        String username = account.getUsername();

        return username != null ? username : "";
    }

    Timeline durationTimeline() {

        return durationTimeline;
    }
}
