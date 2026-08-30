package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetStringReaderTest {

    @Test
    void readsValidFrame() throws IOException {

        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream("5:hello,".getBytes(StandardCharsets.UTF_8)));

        assertEquals("hello", reader.read());
    }

    @Test
    void connectionClosedWhileReadingLengthThrowsIOException() {

        NetStringReader reader = new NetStringReader(new ByteArrayInputStream("12".getBytes(StandardCharsets.UTF_8)));

        IOException failure = assertThrows(IOException.class, reader::read);

        assertTrue(failure.getMessage().contains("Connection closed"),
                   "an early EOF must surface as the plain closed-stream IOException");
    }

    @Test
    void garbageLengthThrowsTypedException() {

        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream("abc:payload,".getBytes(StandardCharsets.UTF_8)));

        NetStringProtocolException failure = assertThrows(NetStringProtocolException.class, reader::read);

        assertTrue(failure.getMessage().contains("Malformed netstring length"),
                   "non-numeric framing must be reported as a protocol violation");
    }

    @Test
    void overflowingLengthThrowsTypedException() {

        // Larger than Integer.MAX_VALUE so parseInt overflows
        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream("99999999999:payload,".getBytes(StandardCharsets.UTF_8)));

        assertThrows(NetStringProtocolException.class, reader::read);
    }

    @Test
    void negativeLengthThrowsTypedException() {

        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream("-5:payload,".getBytes(StandardCharsets.UTF_8)));

        NetStringProtocolException failure = assertThrows(NetStringProtocolException.class, reader::read);

        assertTrue(failure.getMessage().contains("out of range"), "a negative length must be rejected before any read");
    }

    @Test
    void oversizedLengthRejectedBeforeAllocation() {

        String oversized = String.valueOf(NetStringReader.MAX_FRAME_BYTES + 1);

        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream((oversized + ":").getBytes(StandardCharsets.UTF_8)));

        NetStringProtocolException failure = assertThrows(NetStringProtocolException.class, reader::read);

        assertTrue(failure.getMessage().contains("out of range"),
                   "the cap must reject the frame before readNBytes allocates");
    }

    @Test
    void frameAtExactlyTheCapIsAccepted() throws IOException {

        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        frame.write((NetStringReader.MAX_FRAME_BYTES + ":").getBytes(StandardCharsets.US_ASCII));

        byte[] payload = new byte[NetStringReader.MAX_FRAME_BYTES];

        payload[0] = 'x';

        frame.write(payload);

        frame.write(',');

        NetStringReader reader = new NetStringReader(new ByteArrayInputStream(frame.toByteArray()));

        assertEquals(NetStringReader.MAX_FRAME_BYTES, reader.read().length(),
                     "a frame at the cap boundary is legitimate and must round-trip");
    }

    @Test
    void truncatedPayloadThrowsUnexpectedEnd() {

        NetStringReader reader = new NetStringReader(
            new ByteArrayInputStream("10:short,".getBytes(StandardCharsets.UTF_8)));

        IOException failure = assertThrows(IOException.class, reader::read);

        assertTrue(failure.getMessage().contains("Unexpected end of stream"));
    }
}
