package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEvent;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventType;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Routes baresip call events onto the SipCallListener
 * pipeline: maps the wire event to the call state, resolves
 * the owning account through the AOR registry and tracks the
 * calls still alive in the backend. The dispatcher callback
 * is supplied by the client, which owns the listener list and
 * the volume re-application side effect. Runs on the ctrl_tcp
 * dispatcher thread, so the tracking map stays concurrent
 * exactly like the original inline implementation did.
 */
class CallEventProcessor {

    private final AccountAorRegistry accountAorRegistry;

    private final BaresipCallStateMapper callStateMapper = new BaresipCallStateMapper();

    private final Consumer<CallEvent> eventDispatcher;

    /**
     * Calls known to be alive in the backend, keyed by backend
     * call id. Terminal events (CALL_CLOSED) drop the entry,
     * so whatever remains when the process is restarted died
     * with it and can be reported as TERMINATED through the
     * normal listener pipeline.
     */
    private final ConcurrentHashMap<String, CallEvent> activeCalls = new ConcurrentHashMap<>();

    CallEventProcessor(AccountAorRegistry accountAorRegistry, Consumer<CallEvent> eventDispatcher) {

        this.accountAorRegistry = accountAorRegistry;

        this.eventDispatcher = eventDispatcher;
    }

    void handleCallEvent(BaresipCallEvent event, String payload) {

        BaresipLog.debug("TYPE >>> " + event.getType());

        BaresipLog.debug("AOR >>> " + event.getAccountAor());

        BaresipLog.debug("CALL ID >>> " + event.getCallId());

        BaresipLog.debug("PARAM >>> " + event.getParam());

        SipCallState state = callStateMapper.map(event.getType(), event.getParam());

        BaresipLog.debug("STATE >>> " + state);

        if (state == null) {

            BaresipLog.debug("STATE IS NULL");

            if (event.getType() == BaresipCallEventType.UNKNOWN) {

                BaresipLog.warn("UNKNOWN call event dropped: " + payload);
            }

            return;
        }

        // Normalize the AOR before lookup so call events that carry
        // baresip-reported addr-params (,transport=udp) or a default
        // port (:5060) are resolved just like ua events are.
        Long accountId = accountAorRegistry.accountIdForAor(AccountAorRegistry.normalizeAor(event.getAccountAor()));

        BaresipLog.debug("ACCOUNT ID >>> " + accountId);

        if (accountId == null) {

            BaresipLog.warn("ACCOUNT NOT FOUND");

            return;
        }

        CallEvent callEvent = new CallEvent(event.getCallId(), accountId, event.getPeerUri(), state,
            "sendonly".equals(event.getRemoteAudioDir()));

        if (state == SipCallState.TERMINATED || state == SipCallState.FAILED) {

            activeCalls.remove(event.getCallId());

        } else {

            activeCalls.put(event.getCallId(), callEvent);
        }

        BaresipLog.debug("DISPATCHING CALL EVENT >>> " + state);

        BaresipLog.debug("EVENT: " + state + " " + event.getCallId());

        eventDispatcher.accept(callEvent);
    }

    /**
     * Snapshots and clears every call still tracked as alive.
     * Used when the baresip session dies with the restarted
     * process: the caller surfaces each drained call as
     * TERMINATED through the normal listener pipeline.
     */
    List<CallEvent> drainActiveCalls() {

        List<CallEvent> drained = List.copyOf(activeCalls.values());

        activeCalls.clear();

        return drained;
    }
}
