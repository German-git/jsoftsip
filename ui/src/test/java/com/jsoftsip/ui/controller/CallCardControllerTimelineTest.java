package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.core.call.MockSipClient;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the CallCard duration timeline is tied to the
 * controller's lifecycle: it starts on setCall, is replaced (and
 * the previous one stopped) on the next setCall, and is stopped
 * on dispose so a recycled/discarded cell never leaves an orphaned
 * animador ticking.
 */
class CallCardControllerTimelineTest {

    private MockSipClient sipClient;

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @AfterEach
    void tearDown() {

        if (sipClient != null) {

            sipClient.shutdown();
        }
    }

    private CallCardController loadCard() {

        sipClient = new MockSipClient();

        AppContext context = new MockAppContext(sipClient);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CallCard.fxml"));

        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new CallCardController(context));

        try {

            loader.load();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to load CallCard.fxml", exception);
        }

        CallCardController controller = loader.getController();

        SipAccount account = new SipAccount();

        account.setUsername("1001");

        CallLeg call = new CallLeg();

        call.setAccount(account);

        call.setDirection(CallDirection.OUTGOING);

        call.setState(CallState.CONNECTED);

        call.setDestination("sip:alice@demo.org");

        call.setBackendCallId("test-call-id");

        controller.setCall(call);

        return controller;
    }

    @Test
    void setCallStartsADurationTimeline() {

        CallCardController controller = loadCard();

        Timeline timeline = controller.durationTimeline();

        assertNotNull(timeline, "setCall must start a duration timeline");
        assertEquals(Timeline.Status.RUNNING, timeline.getStatus(),
                     "the timeline must be playing while the call is live");
    }

    @Test
    void disposeStopsTheDurationTimeline() {

        CallCardController controller = loadCard();

        Timeline timeline = controller.durationTimeline();

        controller.dispose();

        assertEquals(Timeline.Status.STOPPED, timeline.getStatus(),
                     "dispose must stop the timeline when the cell is discarded");
    }

    @Test
    void setCallReplacesTheTimelineWithoutLeakingThePreviousOne() {

        CallCardController controller = loadCard();

        Timeline first = controller.durationTimeline();

        assertEquals(Timeline.Status.RUNNING, first.getStatus());

        controller.setCall(reloadSameCall());

        Timeline second = controller.durationTimeline();

        assertEquals(Timeline.Status.STOPPED, first.getStatus(),
                     "the previous timeline must be stopped when a new call arrives");

        assertEquals(Timeline.Status.RUNNING, second.getStatus(), "the new timeline must be playing");
    }

    private CallLeg reloadSameCall() {

        SipAccount account = new SipAccount();

        account.setUsername("1001");

        CallLeg call = new CallLeg();

        call.setAccount(account);

        call.setDirection(CallDirection.OUTGOING);

        call.setState(CallState.CONNECTED);

        call.setDestination("sip:bob@example.com");

        call.setBackendCallId("test-call-id-2");

        return call;
    }
}
