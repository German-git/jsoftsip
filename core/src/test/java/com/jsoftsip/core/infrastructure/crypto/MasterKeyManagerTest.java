package com.jsoftsip.core.infrastructure.crypto;

import com.jsoftsip.core.config.PrivateFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterKeyManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeCreatesKeyFileWithOwnerOnlyPermissions() throws IOException {

        Path keyFile = tempDir.resolve("master.key");

        MasterKeyManager.initialize(keyFile);

        assertTrue(Files.exists(keyFile), "initialize must create the master key file");

        assertTrue(PrivateFiles.isOwnerOnly(keyFile), "the key file must be owner-only (0600)");
    }

    @Test
    void initializeIsIdempotent() throws IOException {

        Path keyFile = tempDir.resolve("master.key");

        MasterKeyManager.initialize(keyFile);

        byte[] first = Files.readAllBytes(keyFile);

        MasterKeyManager.initialize(keyFile);

        assertArrayEquals(first, Files.readAllBytes(keyFile), "a second initialize must not overwrite the key");
    }

    @Test
    void loadKeyReturnsAesKeyFromInitializedFile() throws IOException {

        Path keyFile = tempDir.resolve("master.key");

        MasterKeyManager.initialize(keyFile);

        SecretKey key = MasterKeyManager.loadKey(keyFile);

        assertNotNull(key, "loadKey must return a key");

        assertEquals("AES", key.getAlgorithm());

        assertEquals(32, key.getEncoded().length, "the key must be 32 bytes (AES-256)");
    }

    @Test
    void loadKeyStillWorksWhenPermissionsAreLoose() throws IOException {

        Path keyFile = tempDir.resolve("master.key");

        Files.write(keyFile, new byte[32]);

        SecretKey key = MasterKeyManager.loadKey(keyFile);

        assertEquals("AES", key.getAlgorithm(), "a loose-permission file must still load");
    }

    @Test
    void loadKeyRejectsInvalidLength() throws IOException {

        Path keyFile = tempDir.resolve("master.key");

        Files.write(keyFile, new byte[16]);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> MasterKeyManager.loadKey(keyFile));

        assertTrue(exception.getMessage().contains("invalid size"), "the error must mention the invalid key size");
    }

    @Test
    void initializeRestoresKeyFromBackup() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path backupFile = tempDir.resolve("master.key.bak");

        MasterKeyManager.initialize(keyFile);
        byte[] original = Files.readAllBytes(keyFile);

        Files.delete(keyFile);
        MasterKeyManager.initialize(keyFile);

        assertArrayEquals(original, Files.readAllBytes(keyFile), "initialize must restore the key from the backup");
    }

    @Test
    void rotateBacksUpOldKeyAndGeneratesNewOne() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path backupFile = tempDir.resolve("master.key.bak");

        MasterKeyManager.initialize(keyFile);
        byte[] original = Files.readAllBytes(keyFile);

        MasterKeyManager.rotate(keyFile, backupFile);
        byte[] rotated = Files.readAllBytes(keyFile);

        assertArrayEquals(original, Files.readAllBytes(backupFile), "rotate must back up the previous key");
        assertFalse(Arrays.equals(original, rotated), "the rotated key must differ from the original");
        assertEquals(32, rotated.length, "the rotated key must still be 32 bytes");
    }

    @Test
    void prepareRotationStagesNewKeyWithoutTouchingActiveKeyOrBackup() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path backupFile = tempDir.resolve("master.key.bak");

        MasterKeyManager.initialize(keyFile);
        byte[] original = Files.readAllBytes(keyFile);
        Path stagingFile = tempDir.resolve("master.key.staged");

        assertFalse(Files.exists(stagingFile), "no staging file must exist before prepareRotation");

        SecretKey staged = MasterKeyManager.prepareRotation(keyFile, backupFile);

        assertNotNull(staged, "prepareRotation must return the staged key");
        assertEquals("AES", staged.getAlgorithm());
        assertEquals(32, staged.getEncoded().length, "the staged key must be 32 bytes (AES-256)");
        assertTrue(Files.exists(stagingFile), "prepareRotation must create the staging file");
        assertArrayEquals(staged.getEncoded(), Files.readAllBytes(stagingFile),
                          "the staging file must hold the returned key bytes");
        assertTrue(PrivateFiles.isOwnerOnly(stagingFile), "the staging file must be owner-only (0600)");
        assertArrayEquals(original, Files.readAllBytes(keyFile), "prepareRotation must not modify the active key file");
        assertTrue(Files.exists(backupFile), "prepareRotation must not delete the backup");
        assertArrayEquals(original, Files.readAllBytes(backupFile), "prepareRotation must not modify the backup");
    }

    @Test
    void commitRotationPromotesStagedKeyOverActiveKeyAndDeletesBackup() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path backupFile = tempDir.resolve("master.key.bak");

        MasterKeyManager.initialize(keyFile);
        Path stagingFile = tempDir.resolve("master.key.staged");

        SecretKey staged = MasterKeyManager.prepareRotation(keyFile, backupFile);

        MasterKeyManager.commitRotation(keyFile, backupFile);

        assertArrayEquals(staged.getEncoded(), Files.readAllBytes(keyFile),
                          "commitRotation must promote the staged key over the active key");
        assertFalse(Files.exists(stagingFile), "commitRotation must remove the staging file");
        assertFalse(Files.exists(backupFile), "commitRotation must delete the backup");
        assertTrue(PrivateFiles.isOwnerOnly(keyFile), "the promoted key file must remain owner-only (0600)");
    }

    @Test
    void abortRotationDiscardsStagedKeyAndKeepsActiveKeyIntact() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path backupFile = tempDir.resolve("master.key.bak");

        MasterKeyManager.initialize(keyFile);
        byte[] original = Files.readAllBytes(keyFile);

        MasterKeyManager.prepareRotation(keyFile, backupFile);

        MasterKeyManager.abortRotation(keyFile);

        assertFalse(Files.exists(tempDir.resolve("master.key.staged")), "abortRotation must remove the staging file");
        assertArrayEquals(original, Files.readAllBytes(keyFile), "abortRotation must leave the active key untouched");
        assertTrue(Files.exists(backupFile), "abortRotation must leave the backup untouched");
    }

    @Test
    void initializeDiscardsStaleStagedKeyLeftByInterruptedRotation() throws IOException {

        Path keyFile = tempDir.resolve("master.key");
        Path stagingFile = tempDir.resolve("master.key.staged");

        Files.write(stagingFile, new byte[32]);

        MasterKeyManager.initialize(keyFile);

        assertFalse(Files.exists(stagingFile), "initialize must discard an orphan staging file");
        assertTrue(Files.exists(keyFile), "initialize must still create the active key");
    }
}
