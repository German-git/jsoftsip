package com.jsoftsip.nativebridge.baresip;

import java.io.IOException;

/**
 * Typed failure for a ctrl_tcp stream whose netstring framing
 * violates the protocol: an unparseable length, a negative
 * length or a frame exceeding the size cap. Extends IOException
 * so existing catch sites keep handling it unchanged while logs
 * and tests can tell protocol violations apart from transport
 * failures.
 */
final class NetStringProtocolException extends IOException {

    NetStringProtocolException(String message) {

        super(message);
    }
}
