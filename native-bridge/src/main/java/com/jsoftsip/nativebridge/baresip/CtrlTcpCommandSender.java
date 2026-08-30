package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns the ctrl_tcp send path of the baresip client: JSON
 * envelope encoding, fire-and-forget commands, and the two
 * pending-future maps that correlate token-bearing responses
 * with their dial and videodir requests. Responses arrive on
 * the dispatcher thread while callers await on their own
 * threads, so every map access relies on ConcurrentHashMap
 * exactly like the original inline implementation did.
 */
class CtrlTcpCommandSender {

    private final CtrlConnection connection;

    private final ObjectMapper mapper = new ObjectMapper();

    private final CallStartResponseParser callStartResponseParser = new CallStartResponseParser();

    /**
     * Pending dials keyed by the request token sent with
     * each dial command, baresip echoes the token in the
     * response, which lets multiple concurrent dials be
     * correlated per call without relying on response order.
     */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingDialFutures = new ConcurrentHashMap<>();

    /**
     * Pending videodir toggles keyed by the request token sent
     * with each video-direction command. Baresip echoes the
     * token in the response, which lets a toggle be correlated
     * to a single ctrl_tcp round-trip even when responses
     * interleave. Named to parallel pendingDialFutures.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingVideoDirFutures = new ConcurrentHashMap<>();

    /**
     * How long a videodir command waits for baresip to echo the
     * correlation token before giving up and reporting failure.
     * Package-private setter allows tests to shorten it.
     */
    private volatile long videodirTimeoutMs = 3000L;

    CtrlTcpCommandSender(CtrlConnection connection) {

        this.connection = connection;
    }

    /**
     * Sends a fire-and-forget ctrl_tcp command. A dead connection
     * (IOException) is logged and reported as {@code false} instead
     * of throwing RuntimeException, so the JavaFX event-handler
     * thread is never killed by an IO failure: the user gets silence
     * (the button appears not to act) rather than an invisible FX
     * exception that leaves the whole window frozen. Callers that
     * can surface feedback to the user should check the return
     * value.
     */
    boolean sendSimple(String command) {

        try {

            BaresipLog.debug("COMMAND -> " + command);

            // Jackson builds the envelope so any control char,
            // quote or backslash in the command is escaped per
            // the JSON spec instead of corrupting the payload.
            ObjectNode envelope = mapper.createObjectNode();

            envelope.put("command", command);

            connection.sendCommand(mapper.writeValueAsString(envelope));

            return true;

        } catch (IOException exception) {

            BaresipLog.error("ctrl_tcp command failed: " + BaresipLog.sanitizeSecrets(command), exception);

            return false;
        }
    }

    /**
     * Sends the dial command for a fresh correlation token and
     * blocks until the correlated response carries the backend
     * call id, or the five second budget expires. On any failure
     * or timeout the pending entry is dropped so it cannot leak
     * or be matched by a later response, other concurrent dials
     * are left untouched.
     */
    String sendDial(String destination) throws Exception {

        String token = java.util.UUID.randomUUID().toString();

        CompletableFuture<String> future = new CompletableFuture<>();

        pendingDialFutures.put(token, future);

        try {

            // Jackson builds the envelope so a destination
            // containing quotes or backslashes is escaped per
            // the JSON spec, insertion order keeps command
            // first and token second as before.
            ObjectNode envelope = mapper.createObjectNode();

            envelope.put("command", "dial " + destination);

            envelope.put("token", token);

            connection.sendCommand(mapper.writeValueAsString(envelope));

            return future.get(5, TimeUnit.SECONDS);

        } catch (Exception exception) {

            pendingDialFutures.remove(token);

            throw exception;
        }
    }

    /**
     * Toggles video transmission direction on the active call of
     * the menu module: sendrecv (TX on) / recvonly (TX off). The
     * command targets the current baresip call, so this assumes
     * a single active call per account (documented limitation).
     * The token is echoed in the response and correlated by
     * {@link #onResponse}, mirroring the dial flow. Any failure —
     * rejected command, dead connection, or timeout — returns
     * false so the UI toggle never shows a state the backend
     * rejected.
     */
    boolean sendVideoDir(boolean enabled) {

        String token = java.util.UUID.randomUUID().toString();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        pendingVideoDirFutures.put(token, future);

        try {

            String direction = enabled ? "sendrecv" : "recvonly";

            // Jackson builds the envelope for the same JSON
            // safety reason as the dial command.
            ObjectNode envelope = mapper.createObjectNode();

            envelope.put("command", "videodir " + direction);

            envelope.put("token", token);

            String command = mapper.writeValueAsString(envelope);

            BaresipLog.debug("COMMAND -> " + command);

            connection.sendCommand(command);

            return future.get(videodirTimeoutMs, TimeUnit.MILLISECONDS);

        } catch (Exception exception) {

            // Drop this toggle's pending entry on failure or
            // timeout so it cannot leak or be matched by a
            // later response.
            pendingVideoDirFutures.remove(token);

            BaresipLog.warn("Failed to set video transmission enabled=" + enabled, exception);

            return false;
        }
    }

    void setVideodirTimeoutMs(long timeoutMs) {

        this.videodirTimeoutMs = timeoutMs;
    }

    /**
     * Correlates a parsed ctrl_tcp response with its pending
     * request. Videodir toggles are correlated by their own
     * token space, they are checked before dial futures since
     * a token belongs to exactly one pending request.
     */
    void onResponse(String payload) {

        CallStartResponse response = callStartResponseParser.parse(payload);

        String token = response.getToken();

        if (token == null) {

            // Response to a command sent without a token
            // (e.g. uanew/uadel); nothing to correlate.
            return;
        }

        CompletableFuture<Boolean> videoFuture = pendingVideoDirFutures.get(token);

        if (videoFuture != null) {

            pendingVideoDirFutures.remove(token);

            videoFuture.complete(response.isOk());

            return;
        }

        CompletableFuture<String> future = pendingDialFutures.get(token);

        if (future == null) {
            return;
        }

        if (!response.isOk()) {

            pendingDialFutures.remove(token);

            future.completeExceptionally(new IllegalStateException(payload));

            return;
        }

        if (response.getCallId() == null) {

            // The dial has not produced a call id yet, leave
            // the entry registered so a later response for the
            // same token can complete it, or the caller's
            // timeout cleans it up.
            return;
        }

        pendingDialFutures.remove(token);

        future.complete(response.getCallId());
    }

    /**
     * Completes every pending dial and videodir future
     * exceptionally so blocked callers unblock immediately
     * when the baresip process was restarted.
     */
    void failPendingRequests() {

        pendingDialFutures.values().forEach(future -> future.completeExceptionally(new IllegalStateException(
            "Baresip session was restarted")));

        pendingDialFutures.clear();

        pendingVideoDirFutures.values().forEach(future -> future.completeExceptionally(new IllegalStateException(
            "Baresip session was restarted")));

        pendingVideoDirFutures.clear();
    }
}
