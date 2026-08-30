package com.jsoftsip.nativebridge.baresip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class CtrlTcpMessageDispatcher {

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<CtrlTcpResponseListener> responseListeners = new CopyOnWriteArrayList<>();

    private final List<CtrlTcpEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public void dispatch(String payload) {

        try {

            JsonNode root = mapper.readTree(payload);

            if (root.path("response").asBoolean(false)) {

                notifyResponses(payload);

                return;
            }

            if (root.path("event").asBoolean(false)) {

                notifyEvents(payload);
            }

        } catch (Exception exception) {

            BaresipLog.error("Failed to parse ctrl_tcp payload", exception);
        }
    }

    public void addResponseListener(CtrlTcpResponseListener listener) {

        responseListeners.add(listener);
    }

    public void addEventListener(CtrlTcpEventListener listener) {

        eventListeners.add(listener);
    }

    private void notifyResponses(String payload) {

        for (int i = 0; i < responseListeners.size(); i++) {

            try {

                responseListeners.get(i).onResponse(payload);

            } catch (Exception exception) {

                BaresipLog.error("Response listener " + i + " failed", exception);
            }
        }
    }

    private void notifyEvents(String payload) {

        for (int i = 0; i < eventListeners.size(); i++) {

            try {

                eventListeners.get(i).onEvent(payload);

            } catch (Exception exception) {

                BaresipLog.error("Event listener " + i + " failed", exception);
            }
        }
    }
}