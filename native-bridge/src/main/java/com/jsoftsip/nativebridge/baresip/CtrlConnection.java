package com.jsoftsip.nativebridge.baresip;

import java.io.IOException;

/**
 * Transport boundary for the baresip ctrl_tcp protocol.
 * The real implementation reads netstrings from a socket,
 * test doubles feed scripted netstrings in memory, and the
 * client never sees the difference.
 */
public interface CtrlConnection {

    void connect() throws IOException;

    void disconnect();

    void reconnect() throws IOException;

    void sendCommand(String command) throws IOException;

    boolean isConnected();

    /**
     * Registers the fan-out used to deliver every payload
     * parsed from an incoming netstring. Called by the
     * client right after construction, before connect.
     */
    void setMessageDispatcher(CtrlTcpMessageDispatcher dispatcher);
}
