package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipRegistrationState;
import com.jsoftsip.nativebridge.baresip.event.BaresipCallEventType;

import java.util.function.Consumer;

/**
 * Processes baresip ua events (REGISTER_OK, UNREGISTERING and
 * friends): parses the payload, maps the wire type onto the
 * registration state, resolves the account through the AOR
 * registry and hands the resulting event to the dispatcher
 * supplied by the client, which owns the listener list. Runs
 * on the ctrl_tcp dispatcher thread, so the registry lookups
 * and removals rely on its concurrent maps exactly like the
 * original inline implementation did.
 */
class RegistrationEventProcessor {

    private final AccountAorRegistry accountAorRegistry;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Consumer<SipRegistrationEvent> eventDispatcher;

    RegistrationEventProcessor(AccountAorRegistry accountAorRegistry, Consumer<SipRegistrationEvent> eventDispatcher) {

        this.accountAorRegistry = accountAorRegistry;

        this.eventDispatcher = eventDispatcher;
    }

    void handleUaEvent(String payload) {

        try {

            JsonNode root = mapper.readTree(payload);

            if (!root.path("class").asText().equals("ua")) {
                return;
            }

            String type = root.path("type").asText();

            String rawAor = root.path("accountaor").asText();

            String aor = AccountAorRegistry.normalizeAor(rawAor);

            BaresipLog.debug("[UA EVENT] type=" + type + " rawAor=" + rawAor + " normalizedAor=" + aor);

            BaresipLog.debug("[UA EVENT] known AORs: " + accountAorRegistry.knownAors());

            // CREATE and SHUTDOWN are known baresip 4.6.0 ua
            // events with no app-side action: they resolve to
            // their enum constants so the UNKNOWN warn stays
            // reserved for genuinely unknown types
            BaresipCallEventType eventType = uaEventType(type);

            SipRegistrationState state = switch (type) {
                case "REGISTERING" -> SipRegistrationState.REGISTERING;
                case "REGISTER_OK", "REGISTERED" -> SipRegistrationState.REGISTERED;
                case "UNREGISTERING", "UNREGISTERED" -> SipRegistrationState.UNREGISTERED;
                case "REGISTER_FAIL" -> SipRegistrationState.FAILED;
                // Known non-actionable baresip 4.6.0
                // ua events: explicit cases so they
                // drop silently instead of warning
                case "CREATE", "SHUTDOWN" -> null;
                default -> null;
            };

            if (state == null) {

                if (eventType == BaresipCallEventType.UNKNOWN) {

                    BaresipLog.warn("UNKNOWN ua event dropped: type=" + type + " rawAor=" + rawAor);
                }

                return;
            }

            Integer code = null;

            int rawCode = root.path("code").asInt(0);

            if (rawCode != 0) {
                code = rawCode;
            }

            String reason = root.path("reason").asText();

            if (reason == null || reason.isBlank()) {
                reason = null;
            }

            Long accountId = accountAorRegistry.accountIdForAor(aor);

            BaresipLog.debug("[UA EVENT] resolved accountId=" + accountId + " for aor=" + aor);

            if (accountId == null) {
                return;
            }

            // Only a COMPLETED unregister drops the mapping:
            // UNREGISTERING is the attempt
            // starting, and if it never completes (network down)
            // or the UA refreshes right after, later events for
            // this AOR must still resolve their account instead of
            // dying as ACCOUNT NOT FOUND
            if ("UNREGISTERED".equals(type)) {
                accountAorRegistry.removeAor(aor, accountId);
            }

            eventDispatcher.accept(new SipRegistrationEvent(accountId, state, aor, code, reason));

        } catch (Exception e) {
            BaresipLog.error("Failed to parse ua event payload", e);
        }
    }

    /**
     * Maps the wire type of a ua event onto the enum. The ua
     * wire values CREATE and SHUTDOWN are known non-actionable
     * baresip 4.6.0 events, so they resolve to their enum
     * constants instead of UNKNOWN, keeping the UNKNOWN warn
     * exclusive to genuinely unknown types.
     */
    private static BaresipCallEventType uaEventType(String type) {

        return switch (type) {
            case "CREATE" -> BaresipCallEventType.UA_CREATE;
            case "SHUTDOWN" -> BaresipCallEventType.UA_SHUTDOWN;
            default -> BaresipCallEventType.UNKNOWN;
        };
    }
}
