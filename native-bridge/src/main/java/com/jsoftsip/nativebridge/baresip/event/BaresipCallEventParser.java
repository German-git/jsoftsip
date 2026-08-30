package com.jsoftsip.nativebridge.baresip.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsoftsip.nativebridge.baresip.BaresipLog;

public class BaresipCallEventParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public BaresipCallEvent parse(String payload) {

        try {

            JsonNode root = mapper.readTree(payload);

            if (!root.path("event").asBoolean()) {

                return null;
            }

            if (!"call".equals(root.path("class").asText())) {

                return null;
            }

            return new BaresipCallEvent(root.path("id").asText(),

                root.path("accountaor").asText(),

                root.path("peeruri").asText(),

                root.path("direction").asText(),

                parseType(root.path("type").asText()),

                root.path("param").asText(),

                root.path("remoteaudiodir").asText());

        } catch (Exception exception) {

            String snippet = BaresipLog.sanitizeSecrets(payload);

            if (snippet.length() > 200) {

                snippet = snippet.substring(0, 200) + "...";
            }

            BaresipLog.warn("Failed to parse call event: " + snippet, exception);

            return null;
        }
    }

    private BaresipCallEventType parseType(String value) {

        try {

            return BaresipCallEventType.valueOf(value);

        } catch (Exception ignored) {

            // Deliberate silent fallback, unlike the sanitized WARN
            // of the payload parser above: an unknown wire type maps
            // to UNKNOWN and the event processors warn exactly once
            // downstream, so logging here would double-report.
            return BaresipCallEventType.UNKNOWN;
        }
    }
}