package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.core.call.MockSipClient;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.DirectExecutorService;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the in-call video dialog: the dialog must load with a
 * btnVideo toggle that starts in the TX-off state, fires
 * setVideoTransmissionEnabled on click, and flips its icon.
 * The dialog is loaded through FXMLLoader with a
 * {@link MockAppContext} that wires a {@link MockSipClient}.
 * The toggle runs on a background executor and publishes its
 * result through {@code Platform.runLater}, so assertions wait
 * for the FX queue to drain with {@link #flushFx()}.
 */
class VideoCallDialogTest {

    private MockSipClient sipClient;

    private VideoCallDialogController controller;

    private Button btnVideo;

    private Label lblAccount;

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    /**
     * Drains pending Platform.runLater tasks: runLater is
     * FIFO, so once this sentinel executes, every handler
     * queued before it has already run.
     */
    private static void flushFx() throws InterruptedException {

        CountDownLatch drained = new CountDownLatch(1);

        Platform.runLater(drained::countDown);

        assertTrue(drained.await(5, TimeUnit.SECONDS), "the FX queue must drain within the timeout");
    }

    @BeforeEach
    void setUp() {

        sipClient = new MockSipClient();
    }

    private void loadDialog() {

        SipAccount account = new SipAccount();

        account.setId(1L);

        account.setUsername("1001");

        // Domain set so the AOR fallback is well-formed
        // (no display name on this account)
        account.setDomain("demo.org");

        loadDialogWithAccount(account);
    }

    private void loadDialogWithAccount(SipAccount account) {

        AppContext context = new MockAppContext(sipClient, new DirectExecutorService());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/VideoCallDialog.fxml"));

        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new VideoCallDialogController(context));

        BorderPane root;

        try {

            root = loader.load();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to load VideoCallDialog.fxml", exception);
        }

        controller = loader.getController();

        btnVideo = (Button) loader.getNamespace().get("btnVideo");

        lblAccount = (Label) loader.getNamespace().get("lblAccount");

        CallLeg call = new CallLeg();

        call.setAccount(account);

        call.setDirection(CallDirection.OUTGOING);

        call.setState(CallState.CONNECTED);

        call.setDestination("sip:alice@demo.org");

        call.setBackendCallId("test-call-id");

        controller.setCall(call);
    }

    /**
     * btnVideo must exist in the FXML and start in TX-off state.
     */
    @Test
    void btnVideoStartsInTxOffState() {

        loadDialog();

        assertNotNull(btnVideo, "btnVideo must be present in the FXML");

        assertFalse(sipClient.isVideoTransmissionEnabled(), "TX must start disabled");

        FontIcon icon = (FontIcon) btnVideo.getGraphic();

        assertNotNull(icon, "btnVideo must have a graphic");

        assertEquals(BootstrapIcons.CAMERA_VIDEO_OFF_FILL, icon.getIconCode(), "TX-off icon must be camera-video-off");
    }

    /**
     * Clicking btnVideo must call setVideoTransmissionEnabled(true)
     * and flip the icon. The command runs on the UI executor
     * and the state is published back on the FX thread.
     */
    @Test
    void clickingBtnVideoEnablesTxAndFlipsIcon() throws InterruptedException {

        loadDialog();

        btnVideo.fire();

        flushFx();

        assertTrue(sipClient.isVideoTransmissionEnabled(), "clicking must enable TX");

        FontIcon icon = (FontIcon) btnVideo.getGraphic();

        assertEquals(BootstrapIcons.CAMERA_VIDEO_FILL, icon.getIconCode(), "TX-on icon must be camera-video");
    }

    /**
     * Clicking again must disable TX and revert the icon.
     */
    @Test
    void clickingAgainDisablesTxAndRevertsIcon() throws InterruptedException {

        loadDialog();

        btnVideo.fire();

        flushFx();

        btnVideo.fire();

        flushFx();

        assertFalse(sipClient.isVideoTransmissionEnabled(), "clicking again must disable TX");

        FontIcon icon = (FontIcon) btnVideo.getGraphic();

        assertEquals(BootstrapIcons.CAMERA_VIDEO_OFF_FILL, icon.getIconCode(),
                     "TX-off icon must be camera-video-off again");
    }

    /**
     * REQ-2 S2: when the account has no display name the
     * modal must prominently show the AOR (sip:user@domain)
     * on lblAccount, without error.
     */
    @Test
    void accountLabelShowsAorWhenNoDisplayName() {

        loadDialog();

        assertNotNull(lblAccount, "lblAccount must be present in the FXML");

        assertEquals("sip:1001@demo.org", lblAccount.getText(),
                     "AOR fallback must be shown when no display name is set");
    }

    /**
     * REQ-2 S1: when the account has a display name the modal
     * must prominently show that display name on lblAccount.
     */
    @Test
    void accountLabelShowsDisplayNameWhenPresent() {

        SipAccount account = new SipAccount();

        account.setId(2L);

        account.setUsername("1002");

        account.setDomain("demo.org");

        account.setDisplayName("Alice Reyes");

        loadDialogWithAccount(account);

        assertNotNull(lblAccount, "lblAccount must be present in the FXML");

        assertEquals("Alice Reyes", lblAccount.getText(), "display name must be shown when present");
    }
}
