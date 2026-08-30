package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.nativebridge.baresip.event.BaresipCallEvent;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventParser;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallState;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BaresipSipClient implements SipClient, CtrlTcpEventListener, CtrlTcpResponseListener {

    private final List<SipCallListener> callListeners = new CopyOnWriteArrayList<>();

    private final List<SipEventListener> registrationListeners = new CopyOnWriteArrayList<>();

    private final CtrlTcpMessageDispatcher dispatcher;

    private final CtrlConnection connection;

    private final BaresipCallEventParser callEventParser = new BaresipCallEventParser();

    private final BaresipVolumeController volumeController;

    private final ExecutorService volumeExecutor;

    private final boolean ownsVolumeExecutor;

    private final CtrlTcpCommandSender commandSender;

    private final AccountAorRegistry accountAorRegistry = new AccountAorRegistry();

    private final RegistrationEventProcessor registrationEventProcessor = new RegistrationEventProcessor(
        accountAorRegistry, this::notifyRegistrationListeners);

    private final CallEventProcessor callEventProcessor = new CallEventProcessor(accountAorRegistry,
        this::notifyCallListeners);

    private volatile int volume = 100;

    private volatile int microphoneVolume = 100;

    private volatile boolean microphoneMuted = false;

    public BaresipSipClient(String ctrlTcpHost, int ctrlTcpPort, ExecutorService volumeExecutor) {

        this(new BaresipTcpConnection(ctrlTcpHost, ctrlTcpPort), volumeExecutor, false);
    }

    /**
     * Convenience constructor for tests that do not exercise volume
     * control and therefore do not need to share the production executor.
     * The created virtual-thread executor is shut down in {@link #shutdown()}.
     */
    public BaresipSipClient(String ctrlTcpHost, int ctrlTcpPort) {

        this(new BaresipTcpConnection(ctrlTcpHost, ctrlTcpPort), Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    /**
     * Injection point for tests and alternate transports:
     * the client wires the message dispatcher onto whatever
     * CtrlConnection it is given, so the full pipeline can
     * run against an in-memory fake.
     */
    public BaresipSipClient(CtrlConnection connection, ExecutorService volumeExecutor) {

        this(connection, volumeExecutor, false);
    }

    /**
     * Convenience constructor for tests that do not exercise volume
     * control and therefore do not need to share the production executor.
     * The created virtual-thread executor is shut down in {@link #shutdown()}.
     */
    public BaresipSipClient(CtrlConnection connection) {

        this(connection, Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    private BaresipSipClient(CtrlConnection connection, ExecutorService volumeExecutor, boolean ownsVolumeExecutor) {

        this.volumeExecutor = volumeExecutor;
        this.ownsVolumeExecutor = ownsVolumeExecutor;
        volumeController = new BaresipVolumeController(volumeExecutor);

        dispatcher = new CtrlTcpMessageDispatcher();

        dispatcher.addEventListener(this);

        dispatcher.addResponseListener(this);

        this.connection = connection;

        commandSender = new CtrlTcpCommandSender(connection);

        connection.setMessageDispatcher(dispatcher);
    }

    @Override
    public void initialize() {

        try {

            connection.connect();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to connect to ctrl_tcp.", exception);
        }
    }

    @Override
    public void shutdown() {

        connection.disconnect();

        if (ownsVolumeExecutor) {

            volumeExecutor.shutdownNow();
        }
    }

    /**
     * Re-establishes ctrl_tcp after the baresip process was
     * restarted. IO failures surface as false instead of
     * throwing, so the session-recovery flow can report the
     * restart as failed and let the caller roll back.
     */
    public boolean reconnect() {

        try {

            connection.reconnect();

            return true;

        } catch (IOException exception) {

            BaresipLog.error("ctrl_tcp reconnect failed", exception);

            return false;
        }
    }

    /**
     * Drops every piece of call state whose backend died with
     * the restarted process: pending dial futures complete
     * exceptionally so blocked dialers unblock immediately,
     * and each tracked call is surfaced as TERMINATED through
     * the normal listener pipeline so the UI closes its dead
     * call cards. AOR mappings survive on purpose: accounts
     * are re-provisioned by the registration flow right after.
     */
    public void terminateSessionCalls() {

        commandSender.failPendingRequests();

        callEventProcessor.drainActiveCalls().forEach(call -> notifyCallListeners(new CallEvent(call.getCallId(),
            call.getAccountId(), call.getRemoteUri(), SipCallState.TERMINATED)));
    }

    @Override
    public void registerAccount(SipAccountData account) {

        AccountAorRegistry.validateProvisioningIdentity(account.getUsername(), account.getDomain());

        String aor = "sip:" + account.getUsername() + "@" + account.getDomain();

        // NOTE: the password MUST go in the ,auth_pass addr-param.
        // Baresip ignores the userinfo password (src/account.c reads
        // auth_pass only), unescapes %XX sequences in auth_pass
        // (upstream issue #273), and keeps auth_pass out of the
        // reported accountaor, which must match the key stored by
        // setAccountAor. Only the password may be percent-encoded:
        // encoding the username would alter the reported aor and
        // break the accountId correlation.
        String uri = "sip:" + account.getUsername() + "@" + account.getDomain() + ";transport="
            + account.getTransport().toLowerCase() + ";auth_pass="
            + AccountAorRegistry.encodeAuthPass(account.getPassword());

        accountAorRegistry.setAccountAor(account.getId(), aor);

        if (!commandSender.sendSimple("uanew " + uri)) {

            BaresipLog.warn("Account " + account.getId() + " registered in AOR map but uanew "
                + "command did not reach baresip; registration may not activate");
        }
    }

    @Override
    public void unregisterAccount(long accountId) {

        String aor = accountAorRegistry.getAorForAccount(accountId);

        if (aor == null) {
            return;
        }

        if (!commandSender.sendSimple("uadel " + aor)) {

            BaresipLog.warn("uadel command for account " + accountId + " did not reach baresip");
        }
    }

    @Override
    public String getAorForAccount(long accountId) {

        return accountAorRegistry.getAorForAccount(accountId);
    }

    @Override
    public Long accountIdForAor(String aor) {

        return accountAorRegistry.accountIdForAor(aor);
    }

    public void setAccountAor(long accountId, String aor) {

        accountAorRegistry.setAccountAor(accountId, aor);
    }

    @Override
    public String startCall(long accountId, String destination) {

        try {

            accountAorRegistry.requireAorForAccount(accountId);

            return commandSender.sendDial(destination);

        } catch (Exception exception) {

            throw new RuntimeException(
                "Failed to start call to " + BaresipLog.sanitizeSecrets(destination) + " on account " + accountId,
                exception);
        }
    }

    @Override
    public void answerCall(String callId) {

        BaresipLog.info("Accepting call " + callId);

        // "accept [call-id]" targets a specific call, baresip
        // resolves the id via uag_call_find (modules/menu).
        if (!commandSender.sendSimple("accept " + callId)) {

            BaresipLog.warn("Accept command for call " + callId + " did not reach baresip");
        }
    }

    @Override
    public void rejectCall(String callId) {

        BaresipLog.info("Rejecting call " + callId);

        // "hangup [call-id]" targets a specific call, baresip
        // resolves the id via menu_get_call_ua/uag_call_find.
        if (!commandSender.sendSimple("hangup " + callId)) {

            BaresipLog.warn("Reject command for call " + callId + " did not reach baresip");
        }
    }

    @Override
    public void endCall(String callId) {

        BaresipLog.info("Sending hangup for call " + callId);

        if (!commandSender.sendSimple("hangup " + callId)) {

            BaresipLog.warn("Hangup command for call " + callId + " did not reach baresip");
        }
    }

    @Override
    public void holdCall(String callId) {

        BaresipLog.info("Sending hold for call " + callId);

        // "hold [call-id]" targets a specific call, the menu
        // module's dynamic call menu registers this command
        // while a call is active and resolves the id via
        // uag_call_find.
        if (!commandSender.sendSimple("hold " + callId)) {

            BaresipLog.warn("Hold command for call " + callId + " did not reach baresip");
        }
    }

    @Override
    public void resumeCall(String callId) {

        BaresipLog.info("Sending resume for call " + callId);

        // "resume [call-id]" targets a specific call, the menu
        // module's dynamic call menu registers this command
        // while a call is active and resolves the id via
        // uag_call_find.
        if (!commandSender.sendSimple("resume " + callId)) {

            BaresipLog.warn("Resume command for call " + callId + " did not reach baresip");
        }
    }

    /**
     * Toggles video transmission direction on the active call of
     * the menu module: sendrecv (TX on) / recvonly (TX off). The
     * command targets the current baresip call, so this assumes
     * a single active call per account (documented limitation).
     * Any failure — rejected command, dead connection, or
     * timeout — returns false so the UI toggle never shows a
     * state the backend rejected.
     */
    @Override
    public boolean setVideoTransmissionEnabled(boolean enabled) {

        return commandSender.sendVideoDir(enabled);
    }

    void setVideodirTimeoutMs(long timeoutMs) {

        commandSender.setVideodirTimeoutMs(timeoutMs);
    }

    @Override
    public void setVolume(int volume) {

        // Baresip 4.6.0 has no ctrl_tcp volume command, so
        // the volume is applied to the app's PipeWire streams
        // via pactl, the value is also recorded so client
        // state stays consistent and can be re-applied when
        // a new call creates fresh stream ids.
        this.volume = volume;

        BaresipLog.debug("Volume set to " + volume + " (applied via pactl to PipeWire streams)");

        volumeController.setOutputVolume(volume);
    }

    @Override
    public void setMicrophoneVolume(int volume) {

        this.microphoneVolume = volume;

        BaresipLog.debug("Mic volume set to " + volume);

        volumeController.setMicrophoneVolume(volume);
    }

    @Override
    public void setMicrophoneMuted(boolean muted) {

        this.microphoneMuted = muted;

        BaresipLog.debug("Mic muted: " + muted);

        volumeController.setMicrophoneMuted(muted);
    }

    @Override
    public void addRegistrationListener(SipEventListener listener) {

        registrationListeners.add(listener);
    }

    @Override
    public void addCallListener(SipCallListener listener) {

        callListeners.add(listener);
    }

    @Override
    public void onResponse(String payload) {

        commandSender.onResponse(payload);
    }

    @Override
    public void onEvent(String payload) {

        BaresipLog.debug("EVENT RAW: " + payload);

        BaresipCallEvent event = callEventParser.parse(payload);

        if (event == null) {

            BaresipLog.debug("EVENT = null");

            registrationEventProcessor.handleUaEvent(payload);

            return;
        }

        callEventProcessor.handleCallEvent(event, payload);
    }

    private void notifyRegistrationListeners(SipRegistrationEvent event) {

        for (int i = 0; i < registrationListeners.size(); i++) {

            try {

                registrationListeners.get(i).onRegistrationEvent(event);

            } catch (Exception listenerException) {

                BaresipLog.error("Registration listener " + i + " failed", listenerException);
            }
        }
    }

    String normalizeAor(String aor) {

        return AccountAorRegistry.normalizeAor(aor);
    }

    private void notifyCallListeners(CallEvent event) {

        if (event.getState() == SipCallState.ESTABLISHED) {

            // PipeWire creates fresh per-app stream ids for
            // each call, so the stored volumes must be
            // re-applied when a new call establishes.
            volumeController.setOutputVolume(volume);

            volumeController.setMicrophoneVolume(microphoneVolume);

            if (microphoneMuted) {

                volumeController.setMicrophoneMuted(true);
            }
        }

        for (int i = 0; i < callListeners.size(); i++) {

            try {

                callListeners.get(i).onCallEvent(event);

            } catch (Exception listenerException) {

                BaresipLog.error("CallLeg listener " + i + " failed", listenerException);
            }
        }
    }

}