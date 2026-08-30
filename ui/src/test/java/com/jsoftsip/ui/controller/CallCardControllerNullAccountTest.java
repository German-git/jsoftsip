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
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the null-account guard on CallCardController (REQ-2):
 * a call whose account is null must not throw NPE when the card
 * refreshes, and the origin label falls back to empty.
 */
class CallCardControllerNullAccountTest {

    private static MockSipClient sipClient;

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

    private CallCardControllerWithOrigin loadCard() {

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

        Label lblOrigin = (Label) loader.getNamespace().get("lblOrigin");

        return new CallCardControllerWithOrigin(controller, lblOrigin);
    }

    @Test
    void setCallWithNullAccountDoesNotThrowAndSetOriginEmpty() {

        CallCardControllerWithOrigin card = loadCard();

        CallLeg call = new CallLeg();

        call.setAccount(null);
        call.setDirection(CallDirection.OUTGOING);
        call.setState(CallState.CONNECTED);
        call.setDestination("sip:bob@example.com");

        assertDoesNotThrow(() -> card.controller.setCall(call));

        assertEquals("", card.lblOrigin.getText(),
                     "a null account must leave the origin label empty instead of throwing");
    }

    @Test
    void setCallWithAccountMissingUsernameFallsBackToEmpty() {

        CallCardControllerWithOrigin card = loadCard();

        SipAccount account = new SipAccount();

        account.setDisplayName(null);
        account.setUsername(null);

        CallLeg call = new CallLeg();

        call.setAccount(account);
        call.setDirection(CallDirection.OUTGOING);
        call.setState(CallState.CONNECTED);
        call.setDestination("sip:bob@example.com");

        assertDoesNotThrow(() -> card.controller.setCall(call));

        assertEquals("", card.lblOrigin.getText());
    }

    private record CallCardControllerWithOrigin(CallCardController controller, Label lblOrigin) {
    }
}
