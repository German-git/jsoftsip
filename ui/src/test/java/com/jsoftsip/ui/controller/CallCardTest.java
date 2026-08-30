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
import javafx.scene.layout.HBox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the video-transmission toggle on the CallCard button
 * bar. The card is loaded through FXMLLoader with a
 * {@link MockAppContext} that wires a {@link MockSipClient}
 * so the test can verify setVideoTransmissionEnabled calls
 * without a native baresip process. The toggle runs on a
 * background executor and publishes its result through
 * {@code Platform.runLater}, so every assertion waits for the
 * FX queue to drain with {@link #flushFx()}.
 */
class CallCardTest {

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

    private MockSipClient sipClient;

    private CallCardController controller;

    private FXMLLoader loader;

    private CallLeg call;

    private Button btnVideo;

    @BeforeEach
    void setUp() {

        sipClient = new MockSipClient();
    }

    /**
     * Loads the CallCard FXML with a context that supplies a
     * MockSipClient, injects an established outgoing call, and
     * returns the controller plus the btnVideo button.
     */
    private void loadCard() {

        AppContext context = new MockAppContext(sipClient, new DirectExecutorService());

        loader = new FXMLLoader(getClass().getResource("/fxml/CallCard.fxml"));

        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new CallCardController(context));

        HBox root;

        try {

            root = loader.load();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to load CallCard.fxml", exception);
        }

        controller = loader.getController();

        btnVideo = (Button) loader.getNamespace().get("btnVideo");

        SipAccount account = new SipAccount();

        account.setUsername("1001");

        call = new CallLeg();

        call.setAccount(account);

        call.setDirection(CallDirection.OUTGOING);

        call.setState(CallState.CONNECTED);

        call.setDestination("sip:alice@demo.org");

        call.setBackendCallId("test-call-id");

        controller.setCall(call);
    }

    /**
     * btnVideo must exist in the FXML and start in the TX-off
     * state: icon = camera-video-off, tooltip = "Start Video",
     * and the mock client reports TX as disabled.
     */
    @Test
    void btnVideoStartsInTxOffState() {

        loadCard();

        assertNotNull(btnVideo, "btnVideo must be present in the FXML");

        assertFalse(sipClient.isVideoTransmissionEnabled(), "TX must start disabled");

        FontIcon icon = (FontIcon) btnVideo.getGraphic();

        assertNotNull(icon, "btnVideo must have a graphic");

        assertEquals(BootstrapIcons.CAMERA_VIDEO_OFF_FILL, icon.getIconCode(), "TX-off icon must be camera-video-off");
    }

    /**
     * Clicking btnVideo must call setVideoTransmissionEnabled(true)
     * on the SIP client and flip the icon to TX-on. The command
     * runs on the UI executor and the state is published back on
     * the FX thread.
     */
    @Test
    void clickingBtnVideoEnablesTxAndFlipsIcon() throws InterruptedException {

        loadCard();

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

        loadCard();

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
     * Phase 4 integration: clicking btnVideo must not only
     * toggle TX but also fire the videoOpener callback so the
     * caller (CallListCell) can launch the VideoCallDialog.
     */
    @Test
    void btnVideoClickWithVideoOpenerInvokesIt() throws InterruptedException {

        loadCard();

        // The controller must expose a settable videoOpener
        // callback that receives the active call. This is how
        // the ActiveCallsPane wiring connects the card toggle
        // to the modal dialog.
        AtomicReference<CallLeg> openedCall = new AtomicReference<>();

        Consumer<CallLeg> videoOpener = openedCall::set;

        controller.setVideoOpener(videoOpener);

        btnVideo.fire();

        flushFx();

        assertEquals(call, openedCall.get(), "clicking btnVideo must invoke the videoOpener" + " with the active call");
    }

    /**
     * A late result for a leg no longer bound to this card
     * (recycled cell) must be dropped: it may neither flip
     * videoTxEnabled nor open the video dialog of another call.
     */
    @Test
    void staleVideoToggleResultForReplacedLegIsDropped() throws InterruptedException {

        loadCard();

        AtomicReference<CallLeg> openedCall = new AtomicReference<>();

        controller.setVideoOpener(openedCall::set);

        // The toggle is accepted by the backend while the old
        // leg is still bound, only the publication is delayed.
        btnVideo.fire();

        CallLeg otherCall = new CallLeg();

        SipAccount otherAccount = new SipAccount();

        otherAccount.setUsername("1002");

        otherCall.setAccount(otherAccount);

        otherCall.setDirection(CallDirection.OUTGOING);

        otherCall.setState(CallState.CONNECTED);

        otherCall.setDestination("sip:bob@demo.org");

        otherCall.setBackendCallId("other-call-id");

        controller.setCall(otherCall);

        flushFx();

        assertTrue(openedCall.get() == null, "a stale result must not open the video dialog");

        FontIcon icon = (FontIcon) btnVideo.getGraphic();

        assertEquals(BootstrapIcons.CAMERA_VIDEO_OFF_FILL, icon.getIconCode(),
                     "a stale result must not flip the state of the replaced call");
    }

    /**
     * The direction arrow must merge into the account-destination
     * row: lblOrigin holds the account username, lblDirection the
     * direction glyph, and lblDestination only the destination
     * (no "origin -> destination" prefix).
     */
    @Test
    void directionArrowMergesIntoDestinationRow() {

        loadCard();

        Label lblOrigin = (Label) loader.getNamespace().get("lblOrigin");

        Label lblDestination = (Label) loader.getNamespace().get("lblDestination");

        Label lblDirection = (Label) loader.getNamespace().get("lblDirection");

        assertNotNull(lblOrigin, "lblOrigin must be present in the FXML");

        assertEquals("1001", lblOrigin.getText(), "lblOrigin must show the account username");

        assertNotNull(lblDestination, "lblDestination must be present in the FXML");

        assertEquals("sip:alice@demo.org", lblDestination.getText(),
                     "lblDestination must show only the destination" + " (no account prefix)");

        assertNotNull(lblDirection, "lblDirection must be present in the FXML");

        assertEquals("\u21E8", lblDirection.getText(), "lblDirection must show the OUTGOING glyph");

        assertTrue(lblDirection.getStyleClass().contains("direction-outgoing"),
                   "lblDirection must carry the direction-outgoing" + " CSS class");
    }
}
