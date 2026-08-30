package com.jsoftsip.nativebridge.baresip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class NetStringReader {

    /**
     * Upper bound on a single netstring payload. ctrl_tcp
     * messages are small JSON commands and events, so any
     * length beyond this cap means the framing is corrupt and
     * allocating it could exhaust memory before the failure is
     * detected.
     */
    static final int MAX_FRAME_BYTES = 1024 * 1024;

    private final InputStream input;

    public NetStringReader(InputStream input) {

        this.input = input;
    }

    public String read() throws IOException {

        StringBuilder lengthBuilder = new StringBuilder();

        int ch;

        while ((ch = input.read()) != ':') {

            if (ch == -1) {

                throw new IOException("Connection closed.");
            }

            lengthBuilder.append((char) ch);
        }

        // Parse defensively: garbage framing must surface as the
        // typed protocol failure instead of an unchecked
        // NumberFormatException, and the range check below must
        // happen BEFORE any allocation so a corrupt length can
        // never drive a huge or negative read.
        int length;

        try {

            length = Integer.parseInt(lengthBuilder.toString());

        } catch (NumberFormatException malformed) {

            throw new NetStringProtocolException("Malformed netstring length: " + lengthBuilder);
        }

        if (length < 0 || length > MAX_FRAME_BYTES) {

            throw new NetStringProtocolException("Netstring frame length out of range: " + length);
        }

        byte[] payload = input.readNBytes(length);

        if (payload.length != length) {

            throw new IOException("Unexpected end of stream.");
        }

        int comma = input.read();

        if (comma != ',') {

            throw new IOException("Malformed netstring.");
        }

        return new String(payload, StandardCharsets.UTF_8);
    }
}