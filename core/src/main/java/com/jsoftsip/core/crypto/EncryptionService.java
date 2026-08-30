package com.jsoftsip.core.crypto;

public interface EncryptionService {

    String encrypt(String plainText);

    String decrypt(String encryptedText);
}