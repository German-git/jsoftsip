package com.jsoftsip.ui.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitConfirmationPolicyTest {

    @Test
    void confirmsWhenEnabledAndCallsAreActive() {

        assertTrue(ExitConfirmationPolicy.shouldConfirmExit(true, 1));

        assertTrue(ExitConfirmationPolicy.shouldConfirmExit(true, 3));
    }

    @Test
    void doesNotConfirmWhenEnabledWithoutActiveCalls() {

        assertFalse(ExitConfirmationPolicy.shouldConfirmExit(true, 0));
    }

    @Test
    void doesNotConfirmWhenDisabledEvenWithActiveCalls() {

        assertFalse(ExitConfirmationPolicy.shouldConfirmExit(false, 2));
    }

    @Test
    void doesNotConfirmWhenDisabledWithoutActiveCalls() {

        assertFalse(ExitConfirmationPolicy.shouldConfirmExit(false, 0));
    }
}
