package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CtrlTcpConnectionTest {

    @Test
    @Disabled("Requires a running Baresip instance at 127.0.0.1:4444")
    void shouldKeepConnectionOpen() throws Exception {

        CtrlTcpMessageDispatcher dispatcher = new CtrlTcpMessageDispatcher();

        BaresipTcpConnection connection = new BaresipTcpConnection("127.0.0.1", 4444, dispatcher);

        connection.connect();

        connection.sendCommand("{\"command\":\"help\"}");

        Thread.sleep(2000);

        connection.sendCommand("{\"command\":\"reginfo\"}");

        Thread.sleep(2000);

        connection.sendCommand("{\"command\":\"uastat\"}");

        Thread.sleep(10000);

        connection.disconnect();
    }

    @Test
    @Disabled("Requires a running Baresip instance at 127.0.0.1:4444")
    void shouldReceiveEvents() throws Exception {

        CtrlTcpMessageDispatcher dispatcher = new CtrlTcpMessageDispatcher();

        dispatcher.addResponseListener(response -> System.out.println("RESPONSE >>> " + response));

        dispatcher.addEventListener(event -> System.out.println("EVENT >>> " + event));

        BaresipTcpConnection connection = new BaresipTcpConnection("127.0.0.1", 4444, dispatcher);

        connection.connect();

        connection.sendCommand("{\"command\":\"dial sip:1002@192.168.0.97\"}");

        Thread.sleep(30000);

        connection.disconnect();
    }
}