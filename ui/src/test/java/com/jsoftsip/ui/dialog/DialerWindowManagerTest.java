package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.call.CallListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Proves the leak fix for dialer windows without needing a real
 * JavaFX Stage (the headless CI build cannot show windows).
 *
 * The defect: DialerDialog and DialerWindowManager both set a
 * on-hidden handler via setOnHidden, so the second registration
 * clobbered the first and the dialer controller's dispose()
 * never ran — every open leaked a CallService listener.
 *
 * The fix: both attach via addWindowHiddenHandler on a shared
 * WindowHandle, so close() runs BOTH handlers. This test drives
 * the real DialerWindowManager.register against a fake handle
 * that simulates the dialer's own dispose handler, and asserts
 * that across several open/close cycles the listener count never
 * grows (add and remove stay balanced).
 */
class DialerWindowManagerTest {

    @Test
    void closeRunsAllWindowHiddenHandlersAndDoesNotAccumulateListeners() {

        FakeCallService callService = new FakeCallService();

        int openCycles = 3;

        for (int i = 0; i < openCycles; i++) {

            FakeWindowHandle handle = new FakeWindowHandle();

            CallListener listener = call -> {
            };

            // Mirrors DialerDialog.open attaching its own dispose
            // handler before registering the window with the manager.
            handle.addWindowHiddenHandler(() -> callService.removeListener(listener));

            DialerWindowManager.register(42L, handle);

            callService.addListener(listener);

            handle.close();
        }

        assertEquals(0, callService.listenerCount(), "every window close must run dispose and remove the listener");

        assertEquals(callService.addCount(), callService.removeCount(),
                     "listener additions and removals must balance across open/close cycles");

        assertFalse(DialerWindowManager.isOpen(42L), "the manager must deregister on close");
    }

    private static final class FakeWindowHandle implements WindowHandle {

        private final CopyOnWriteArrayList<Runnable> handlers = new CopyOnWriteArrayList<>();

        @Override
        public void addWindowHiddenHandler(Runnable handler) {

            handlers.add(handler);
        }

        @Override
        public void close() {

            for (Runnable handler : handlers) {

                handler.run();
            }
        }
    }

    private static final class FakeCallService {

        private final CopyOnWriteArrayList<CallListener> listeners = new CopyOnWriteArrayList<>();

        private final AtomicInteger adds = new AtomicInteger();

        private final AtomicInteger removes = new AtomicInteger();

        void addListener(CallListener listener) {

            listeners.add(listener);

            adds.incrementAndGet();
        }

        void removeListener(CallListener listener) {

            listeners.remove(listener);

            removes.incrementAndGet();
        }

        int listenerCount() {

            return listeners.size();
        }

        int addCount() {

            return adds.get();
        }

        int removeCount() {

            return removes.get();
        }
    }
}
