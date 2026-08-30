package com.jsoftsip.core.infrastructure.crypto;

import com.jsoftsip.core.config.ApplicationPaths;
import com.jsoftsip.core.config.PrivateFiles;
import com.jsoftsip.core.logging.JSoftSipLog;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;

public final class MasterKeyManager {

    private static final int KEY_SIZE = 32;

    private MasterKeyManager() {
    }

    public static void initialize() {

        initialize(ApplicationPaths.getMasterKeyFile());
    }

    static void initialize(Path keyFile) {

        discardStaleStaging(keyFile);

        if (Files.exists(keyFile)) {

            return;
        }

        Path backupFile = backupPathFor(keyFile);

        try {

            if (Files.exists(backupFile)) {

                PrivateFiles.copy(backupFile, keyFile);
                JSoftSipLog.info("Restored master key from backup");
                return;
            }

            byte[] key = new byte[KEY_SIZE];

            new SecureRandom().nextBytes(key);

            PrivateFiles.write(keyFile, key);
            PrivateFiles.copy(keyFile, backupFile);

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to create master key", exception);

            throw new IllegalStateException("Failed to create master key", exception);
        }
    }

    public static void rotate() {

        rotate(ApplicationPaths.getMasterKeyFile(), ApplicationPaths.getMasterKeyBackupFile());
    }

    static void rotate(Path keyFile, Path backupFile) {

        try {

            if (Files.exists(keyFile)) {

                PrivateFiles.copy(keyFile, backupFile);
            }

            byte[] key = new byte[KEY_SIZE];

            new SecureRandom().nextBytes(key);

            PrivateFiles.write(keyFile, key);

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to rotate master key", exception);

            throw new IllegalStateException("Failed to rotate master key", exception);
        }
    }

    public static void writeKey(SecretKey key) {

        writeKey(ApplicationPaths.getMasterKeyFile(), key);
    }

    static void writeKey(Path keyFile, SecretKey key) {

        try {

            PrivateFiles.write(keyFile, key.getEncoded());

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to write master key", exception);

            throw new IllegalStateException("Failed to write master key", exception);
        }
    }

    /**
     * Generates a fresh master key and stages it in a sibling file without
     * touching the active key or its backup. The caller must re-encrypt every
     * dependent secret under the returned key and then invoke
     * {@link #commitRotation()}, when re-encryption fails, discard the staged
     * key with {@link #abortRotation()}.
     */
    public static SecretKey prepareRotation() {

        return prepareRotation(ApplicationPaths.getMasterKeyFile(), ApplicationPaths.getMasterKeyBackupFile());
    }

    static SecretKey prepareRotation(Path keyFile, Path backupFile) {

        SecretKey stagedKey = newKey();

        try {

            PrivateFiles.write(stagingPathFor(keyFile), stagedKey.getEncoded());

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to stage master key rotation", exception);

            throw new IllegalStateException("Failed to stage master key rotation", exception);
        }

        return stagedKey;
    }

    /**
     * Promotes the previously staged key over the active key file with an
     * atomic move and then deletes the previous backup so the old key can no
     * longer be recovered. Call this only after every dependent secret has
     * been re-encrypted under the staged key. If the promotion fails, the
     * staging file is preserved because it still holds the only copy of the
     * key that protects the re-encrypted secrets.
     */
    public static void commitRotation() {

        commitRotation(ApplicationPaths.getMasterKeyFile(), ApplicationPaths.getMasterKeyBackupFile());
    }

    static void commitRotation(Path keyFile, Path backupFile) {

        Path stagingFile = stagingPathFor(keyFile);

        try {

            try {

                Files.move(stagingFile, keyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException unsupported) {

                Files.move(stagingFile, keyFile, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to promote staged master key", exception);

            throw new IllegalStateException(
                "Credential rows were already re-keyed but promoting the new master key failed; "
                    + "the staging file at " + stagingFile + " holds the correct key and must be recovered manually",
                exception);
        }

        deleteBackup(backupFile);
    }

    /**
     * Discards a staged key left behind by a failed rotation. Best effort by
     * contract so it is safe to call directly from failure paths.
     */
    public static void abortRotation() {

        abortRotation(ApplicationPaths.getMasterKeyFile());
    }

    static void abortRotation(Path keyFile) {

        try {

            Files.deleteIfExists(stagingPathFor(keyFile));

        } catch (IOException exception) {

            JSoftSipLog.warn("Failed to delete staged master key", exception);
        }
    }

    private static SecretKey newKey() {

        byte[] key = new byte[KEY_SIZE];

        new SecureRandom().nextBytes(key);

        return new SecretKeySpec(key, "AES");
    }

    private static void deleteBackup(Path backupFile) {

        try {

            if (Files.exists(backupFile)) {

                Files.delete(backupFile);
            }

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to delete master key backup", exception);

            throw new IllegalStateException("Failed to delete master key backup", exception);
        }
    }

    public static SecretKey loadKey() {

        return loadKey(ApplicationPaths.getMasterKeyFile());
    }

    static SecretKey loadKey(Path keyFile) {

        try {

            if (!PrivateFiles.isOwnerOnly(keyFile)) {

                JSoftSipLog.warn("Master key file permissions are not owner-only (0600)");
            }

            byte[] keyBytes = Files.readAllBytes(keyFile);

            if (keyBytes.length != KEY_SIZE) {

                throw new IllegalStateException(
                    "Master key file has invalid size: expected " + KEY_SIZE + " bytes but found " + keyBytes.length);
            }

            return new SecretKeySpec(keyBytes, "AES");

        } catch (IOException exception) {

            JSoftSipLog.error("Failed to load master key", exception);

            throw new IllegalStateException("Failed to load master key", exception);
        }
    }

    private static Path backupPathFor(Path keyFile) {

        return keyFile.resolveSibling(keyFile.getFileName() + ".bak");
    }

    private static Path stagingPathFor(Path keyFile) {

        return keyFile.resolveSibling(keyFile.getFileName() + ".staged");
    }

    private static void discardStaleStaging(Path keyFile) {

        try {

            if (Files.deleteIfExists(stagingPathFor(keyFile))) {

                JSoftSipLog.info("Discarded stale staged master key left by an interrupted rotation");
            }

        } catch (IOException exception) {

            JSoftSipLog.warn("Failed to discard stale staged master key", exception);
        }
    }
}
