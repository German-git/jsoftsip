package com.jsoftsip.core.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmEncryptionServiceTest {

    @TempDir
    Path tempDir;

    private AesGcmEncryptionService service;

    @BeforeEach
    void setUp() {

        Path keyFile = tempDir.resolve("master.key");

        MasterKeyManager.initialize(keyFile);

        service = new AesGcmEncryptionService(keyFile);
    }

    @Test
    void roundTripRestoresOriginalText() {

        String plainText = "s3cret-password";

        String encrypted = service.encrypt(plainText);

        assertEquals(plainText, service.decrypt(encrypted));
    }

    @Test
    void roundTripSupportsNonAsciiText() {

        String plainText = "contraseña ñ José 密码";

        String encrypted = service.encrypt(plainText);

        assertEquals(plainText, service.decrypt(encrypted));
    }

    @Test
    void encryptProducesUniqueCiphertextPerCall() {

        String plainText = "same-value";

        String first = service.encrypt(plainText);

        String second = service.encrypt(plainText);

        assertNotEquals(first, second, "IV must be random per encryption");
    }

    @Test
    void tamperedCiphertextIsRejected() {

        String encrypted = service.encrypt("integrity-check");

        byte[] bytes = Base64.getDecoder().decode(encrypted);

        bytes[bytes.length / 2] ^= 0x01;

        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }

    @Test
    void decryptWithDifferentKeyFails() {

        Path otherKeyFile = tempDir.resolve("other.key");

        MasterKeyManager.initialize(otherKeyFile);

        AesGcmEncryptionService otherService = new AesGcmEncryptionService(otherKeyFile);

        String encrypted = service.encrypt("cross-key");

        assertThrows(IllegalStateException.class, () -> otherService.decrypt(encrypted));
    }
}