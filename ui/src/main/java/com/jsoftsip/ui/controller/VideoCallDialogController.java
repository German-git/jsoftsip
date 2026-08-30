package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.video.VideoFrameSource;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.IconFactory;
import com.jsoftsip.ui.util.AccountIdentityFormatter;
import com.jsoftsip.ui.video.VideoView;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * In-call video dialog controller. Renders the remote video
 * preview in a {@link VideoView} and exposes a TX toggle that
 * maps to {@code videodir} on the baresip control channel.
 *
 * <p>The video source (if available) is resolved lazily by
 * {@link AppContext#getVideoFrameSource(long)}, when no source
 * exists (MOCK backend) the placeholder is shown instead.
 */
public class VideoCallDialogController {

    @FXML
    private StackPane videoContainer;

    @FXML
    private Label lblAccount;

    @FXML
    private Button btnVideo;

    private CallLeg call;

    private boolean videoTxEnabled;

    private VideoView videoView;

    private final AppContext context;

    private final CallService callService;

    private final ExecutorService uiExecutor;

    public VideoCallDialogController(AppContext context) {

        this.context = context;

        this.callService = context.getCallService();

        this.uiExecutor = context.getUiExecutor();
    }

    @FXML
    private void initialize() {

        btnVideo.setOnAction(event -> toggleVideo());

        // Start with the TX-off icon so the button is not
        // blank before setCall() runs refresh()
        videoTxEnabled = false;

        IconFactory.configureI18nButton(btnVideo, BootstrapIcons.CAMERA_VIDEO_OFF_FILL, "video.start.tooltip");
    }

    /**
     * Binds the controller to the active call: creates the
     * video view for the call's account, starts rendering and
     * applies the initial icon state.
     */
    public void setCall(CallLeg call) {

        this.call = call;

        videoTxEnabled = false;

        long accountId = call.getAccount() != null ? call.getAccount().getId() : 0L;

        // REQ-2: show the local account identity prominently
        // (display name, or AOR fallback when absent). Guarded
        // so a null account never throws.
        if (lblAccount != null) {

            lblAccount.setText(resolveAccountIdentity(call.getAccount()));
        }

        Optional<VideoFrameSource> source = context.getVideoFrameSource(accountId);

        videoView = new VideoView(source, accountId);

        videoContainer.getChildren().add(videoView.node());

        videoView.start();

        refresh();
    }

    /**
     * Resolves the prominent local account identity: the
     * display name when present, otherwise the AOR
     * {@code sip:<username>@<domain>}. Never throws on a
     * null account or null fields.
     */
    private String resolveAccountIdentity(SipAccount account) {

        return AccountIdentityFormatter.formatProminent(account);
    }

    private void toggleVideo() {

        if (call == null || !callService.isVideoSupported()) {
            return;
        }

        boolean target = !videoTxEnabled;

        CallLeg requestedCall = call;

        JSoftSipLog.info("" + (target ? "Start video TX" : "Stop video TX") + " " + call.getBackendCallId());

        // The videodir command blocks up to videodirTimeoutMs
        // on the bridge, so it must stay off the FX thread,
        // the outcome is published back through Platform.runLater.
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
     * thread. Results belonging to a leg no longer bound to
     * this dialog are dropped so a late answer can never flip
     * the state of another call.
     */
    private void handleVideoToggleResult(CallLeg requestedCall, boolean target, boolean accepted) {

        if (!accepted || call != requestedCall) {
            return;
        }

        videoTxEnabled = target;

        refresh();
    }

    private void refresh() {

        IconFactory.configureI18nButton(btnVideo,
                                        videoTxEnabled
                                            ? BootstrapIcons.CAMERA_VIDEO_FILL
                                            : BootstrapIcons.CAMERA_VIDEO_OFF_FILL,
                                        videoTxEnabled ? "video.stop.tooltip" : "video.start.tooltip");
    }

    /**
     * Stops the frame-polling timer and clears the video
     * node so the dialog can be hidden without leaking
     * background work.
     */
    public void dispose() {

        if (videoView != null) {

            videoView.dispose();
        }

        if (videoContainer != null) {

            videoContainer.getChildren().clear();
        }
    }

    /**
     * Returns the owning window so the dialog can set
     * modality and ownership from the factory.
     */
    public Optional<Window> owningWindow() {

        if (btnVideo == null || btnVideo.getScene() == null) {

            return Optional.empty();
        }

        return Optional.of(btnVideo.getScene().getWindow());
    }
}
