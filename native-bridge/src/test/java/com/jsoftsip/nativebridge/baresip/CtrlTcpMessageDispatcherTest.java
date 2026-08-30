package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CtrlTcpMessageDispatcherTest {

    private final CtrlTcpMessageDispatcher dispatcher = new CtrlTcpMessageDispatcher();

    @Test
    void failingEventListenerDoesNotAbortRemainingListeners() {

        AtomicBoolean firstCalled = new AtomicBoolean();

        AtomicBoolean secondCalled = new AtomicBoolean();

        dispatcher.addEventListener(payload -> {
            firstCalled.set(true);
            throw new IllegalStateException("boom");
        });

        dispatcher.addEventListener(payload -> secondCalled.set(true));

        dispatcher.dispatch("{\"event\": true, \"type\": \"call_established\"}");

        assertTrue(firstCalled.get(), "the failing listener must still be invoked");
        assertTrue(secondCalled.get(), "the remaining listener must run after the failure");
    }

    @Test
    void failingResponseListenerDoesNotAbortRemainingListeners() {

        AtomicBoolean firstCalled = new AtomicBoolean();

        AtomicBoolean secondCalled = new AtomicBoolean();

        dispatcher.addResponseListener(payload -> {
            firstCalled.set(true);
            throw new IllegalStateException("boom");
        });

        dispatcher.addResponseListener(payload -> secondCalled.set(true));

        dispatcher.dispatch("{\"response\": true, \"id\": 1}");

        assertTrue(firstCalled.get());
        assertTrue(secondCalled.get());
    }

    @Test
    void malformedPayloadIsLoggedAndDoesNotReachListeners() {

        AtomicBoolean eventCalled = new AtomicBoolean();

        dispatcher.addEventListener(payload -> eventCalled.set(true));

        dispatcher.dispatch("this is not json {");

        assertTrue(!eventCalled.get());
    }
}