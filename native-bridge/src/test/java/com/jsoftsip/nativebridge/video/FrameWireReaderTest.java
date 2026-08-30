package com.jsoftsip.nativebridge.video;

import com.jsoftsip.core.video.PixelFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameWireReaderTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private static byte[] validFrameBytes() throws IOException {

        // YUV420P 4x2 with stride 16: Y=32 bytes, U=8, V=8
        int width = 4;
        int height = 2;
        int stride = 16;
        int pixelBytes = stride * height + 2 * (stride / 2) * (height / 2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        data.writeInt(21 + pixelBytes);
        data.writeInt(width);
        data.writeInt(height);
        data.writeByte(PixelFormat.YUV420P.ordinal());
        data.writeLong(1_234_567_890L);
        data.writeInt(stride);

        for (int i = 0; i < pixelBytes; i++) {
            data.writeByte(i);
        }

        return out.toByteArray();
    }

    @Test
    void readsACompleteFrameWithItsAor() throws IOException {

        Optional<FrameWireReader.WireFrame> result = FrameWireReader.read(new ByteArrayInputStream(validFrameBytes()));

        assertTrue(result.isPresent());

        FrameWireReader.WireFrame frame = result.get();

        assertEquals(ALICE_AOR, frame.aor());
        assertEquals(4, frame.width());
        assertEquals(2, frame.height());
        assertEquals(PixelFormat.YUV420P, frame.pixelFormat());
        assertEquals(1_234_567_890L, frame.timestampNanos());
        assertEquals(16, frame.stride());

        byte[] pixels = frame.pixels();
        assertEquals(48, pixels.length);
        for (int i = 0; i < pixels.length; i++) {
            assertEquals((byte) i, pixels[i]);
        }
    }

    @Test
    void extractsMultibyteAorsAsUtf8() throws IOException {

        String aor = "sip:jos\u00e9@example.com";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aorBytes = aor.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aorBytes.length);
        data.write(aorBytes);

        byte[] frame = validFrameBytes();
        // strip the routing prefix of the canned frame
        int prefix = 2 + "sip:alice@example.com".getBytes(StandardCharsets.UTF_8).length;
        data.write(frame, prefix, frame.length - prefix);

        Optional<FrameWireReader.WireFrame> result = FrameWireReader.read(new ByteArrayInputStream(out.toByteArray()));

        assertTrue(result.isPresent());
        assertEquals(aor, result.get().aor());
    }

    @Test
    void returnsEmptyOnCleanEofAtFrameBoundary() throws IOException {

        Optional<FrameWireReader.WireFrame> result = FrameWireReader.read(new ByteArrayInputStream(new byte[0]));

        assertTrue(result.isEmpty());
    }

    @Test
    void throwsOnTruncatedFrame() throws IOException {

        byte[] bytes = validFrameBytes();

        // cut the payload short: the declared length is never met
        byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - 10);

        assertThrows(WireFormatException.class, () -> FrameWireReader.read(new ByteArrayInputStream(truncated)));
    }

    @Test
    void throwsOnMalformedLengthShorterThanHeader() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        // length 5 can never cover the 21 byte header
        data.writeInt(5);
        data.write(new byte[5]);

        assertThrows(WireFormatException.class,
                     () -> FrameWireReader.read(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void throwsOnFrameLengthBeyondTheCap() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        // Declared body larger than the cap, no body bytes sent:
        // the reader must reject the header before allocating.
        data.writeInt(FrameWireReader.MAX_FRAME_BYTES + 1);

        WireFormatException failure = assertThrows(WireFormatException.class,
                                                   () -> FrameWireReader.read(new ByteArrayInputStream(
                                                       out.toByteArray())));

        assertTrue(failure.getMessage().contains("cap"),
                   "an oversized declared length must be reported against the cap");
    }

    @Test
    void hugeCorruptLengthFailsWithoutAllocating() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        // Near 2^31: honoring this length would allocate two
        // gigabytes up front and OOM instead of throwing
        data.writeInt(Integer.MAX_VALUE);

        WireFormatException failure = assertThrows(WireFormatException.class,
                                                   () -> FrameWireReader.read(new ByteArrayInputStream(
                                                       out.toByteArray())));

        assertTrue(failure.getMessage().contains("exceeds the cap"));
    }

    @Test
    void throwsOnUnknownPixelFormat() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        int pixelBytes = 32;
        data.writeInt(21 + pixelBytes);
        data.writeInt(4);
        data.writeInt(2);
        data.writeByte(99); // unknown format ordinal
        data.writeLong(0L);
        data.writeInt(16);
        data.write(new byte[pixelBytes]);

        assertThrows(WireFormatException.class,
                     () -> FrameWireReader.read(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void throwsOnNonPositiveDimensions() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        byte[] aor = ALICE_AOR.getBytes(StandardCharsets.UTF_8);
        data.writeShort(aor.length);
        data.write(aor);

        int pixelBytes = 32;
        data.writeInt(21 + pixelBytes);
        data.writeInt(0); // zero width
        data.writeInt(2);
        data.writeByte(PixelFormat.YUV420P.ordinal());
        data.writeLong(0L);
        data.writeInt(16);
        data.write(new byte[pixelBytes]);

        assertThrows(WireFormatException.class,
                     () -> FrameWireReader.read(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    void readsTwoConsecutiveFramesFromOneStream() throws IOException {

        byte[] one = validFrameBytes();
        byte[] two = validFrameBytes();

        byte[] both = new byte[one.length + two.length];
        System.arraycopy(one, 0, both, 0, one.length);
        System.arraycopy(two, 0, both, one.length, two.length);

        ByteArrayInputStream stream = new ByteArrayInputStream(both);

        Optional<FrameWireReader.WireFrame> first = FrameWireReader.read(stream);
        Optional<FrameWireReader.WireFrame> second = FrameWireReader.read(stream);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertArrayEquals(first.get().pixels(), second.get().pixels());
        assertEquals(first.get().aor(), second.get().aor());
        assertTrue(FrameWireReader.read(stream).isEmpty());
    }
}