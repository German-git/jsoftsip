package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallStartResponseParser {

    private static final Pattern CALL_ID_PATTERN = Pattern.compile("call id:\\s*(\\S+)");

    private final ObjectMapper mapper = new ObjectMapper();

    public CallStartResponse parse(String payload) {

        try {

            JsonNode root = mapper.readTree(payload);

            boolean ok = root.path("ok").asBoolean(false);

            String data = root.path("data").asText();

            String callId = extractCallId(data);

            String token = root.path("token").asText(null);

            return new CallStartResponse(ok, callId, token);

        } catch (Exception exception) {

            BaresipLog.warn("Failed to parse dial response", exception);

            return new CallStartResponse(false, null, null);
        }
    }

    private String extractCallId(String data) {

        Matcher matcher = CALL_ID_PATTERN.matcher(data);

        if (matcher.find()) {

            return matcher.group(1);
        }

        return null;
    }
}
