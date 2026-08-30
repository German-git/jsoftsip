package com.jsoftsip.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Writes and copies files with owner-only (0600) permissions so
 * secrets never stay readable by other local users. POSIX
 * permissions are set best-effort: on filesystems without POSIX
 * support the write still succeeds with the platform defaults.
 */
public final class PrivateFiles {

    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ,
                                                                          PosixFilePermission.OWNER_WRITE);

    private PrivateFiles() {
    }

    public static void write(Path path, byte[] bytes) throws IOException {

        if (!Files.exists(path)) {

            createWithOwnerOnly(path);
        }

        Files.write(path, bytes);

        restrict(path);
    }

    public static void write(Path path, List<String> lines) throws IOException {

        write(path, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }

    public static void copy(Path source, Path target) throws IOException {

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        restrict(target);
    }

    /**
     * Reports whether the file carries exactly the owner-only
     * permission set. Filesystems without POSIX support report
     * true so the check never blocks a read.
     */
    public static boolean isOwnerOnly(Path path) {

        try {

            return Files.getPosixFilePermissions(path).equals(OWNER_ONLY);

        } catch (UnsupportedOperationException | IOException exception) {

            return true;
        }
    }

    private static void createWithOwnerOnly(Path path) throws IOException {

        try {

            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));

        } catch (UnsupportedOperationException unsupported) {

            Files.createFile(path);
        }
    }

    private static void restrict(Path path) {

        try {

            Files.setPosixFilePermissions(path, OWNER_ONLY);

        } catch (UnsupportedOperationException | IOException exception) {

            // Non-POSIX filesystem: best effort only
        }
    }
}