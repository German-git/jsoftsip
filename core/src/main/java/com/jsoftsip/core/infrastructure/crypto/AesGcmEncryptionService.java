package com.jsoftsip.core.infrastructure.crypto;

import com.jsoftsip.core.crypto.EncryptionService;
import com.jsoftsip.core.logging.JSoftSipLog;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmEncryptionService implements EncryptionService {

    private static final int IV_LENGTH = 12;

    private static final int TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public AesGcmEncryptionService() {
        this.secretKey = MasterKeyManager.loadKey();
    }

    public AesGcmEncryptionService(SecretKey key) {
        this.secretKey = key;
    }

    AesGcmEncryptionService(Path keyFile) {
        this.secretKey = MasterKeyManager.loadKey(keyFile);
    }

    @Override
    public String encrypt(String plainText) {

        try {

            byte[] iv = new byte[IV_LENGTH];

            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);

            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception exception) {

            JSoftSipLog.error("Encryption failed", exception);

            throw new IllegalStateException("Encryption failed", exception);
        }
    }

    @Override
    public String decrypt(String encryptedText) {

        try {

            byte[] allBytes = Base64.getDecoder().decode(encryptedText);

            ByteBuffer buffer = ByteBuffer.wrap(allBytes);

            byte[] iv = new byte[IV_LENGTH];

            buffer.get(iv);

            byte[] cipherBytes = new byte[buffer.remaining()];

            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            JSoftSipLog.error("Decryption failed", exception);

            throw new IllegalStateException("Decryption failed", exception);
        }
    }
}