package com.jsoftsip.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the owner-only file primitives:
 * every secret in the app lands on disk through this class, so
 * the 0600 guarantee and the overwrite semantics are pinned here.
 */
class PrivateFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void writeCreatesTheFileWithOwnerOnlyPermissions() throws IOException {

        Path target = tempDir.resolve("secret.bin");

        PrivateFiles.write(target, new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target));
        assertTrue(PrivateFiles.isOwnerOnly(target), "a freshly written secret must be owner-only (0600)");
    }

    @Test
    void writeOverwritesAnExistingFileAndKeepsItOwnerOnly() throws IOException {

        Path target = tempDir.resolve("secret.bin");

        PrivateFiles.write(target, "first".getBytes(StandardCharsets.UTF_8));

        PrivateFiles.write(target, "second".getBytes(StandardCharsets.UTF_8));

        assertEquals("second", Files.readString(target), "an existing file must be replaced");
        assertTrue(PrivateFiles.isOwnerOnly(target), "the rewrite must keep the owner-only permission set");
    }

    @Test
    void writeLinesJoinsTheListIntoTheFile() throws IOException {

        Path target = tempDir.resolve("config-lines");

        List<String> lines = List.of("audio_player\tpulse", "call_max_calls\t4");

        PrivateFiles.write(target, lines);

        assertEquals(lines, Files.readAllLines(target), "the written lines must round-trip");
        assertTrue(PrivateFiles.isOwnerOnly(target));
    }

    @Test
    void copyReplacesTheTargetAndRestrictsItsPermissions() throws IOException {

        Path source = tempDir.resolve("source");

        Files.writeString(source, "payload");

        // A pre-existing permissive target must be replaced and
        // restricted, not merged or kept as-is
        Path target = tempDir.resolve("target");

        Files.writeString(target, "stale");

        PrivateFiles.copy(source, target);

        assertEquals("payload", Files.readString(target), "copy must replace the stale content");
        assertTrue(PrivateFiles.isOwnerOnly(target), "the copied secret must end up owner-only");
    }
}
