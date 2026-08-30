package com.jsoftsip.core.infrastructure.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteSettingRepositoryTest {

    @TempDir
    Path tempDir;

    private SQLiteSettingRepository repository;

    @BeforeEach
    void setUp() {

        DatabaseManager.useDatabaseFile(tempDir.resolve("test.db"));

        DatabaseInitializer.migrate();

        repository = new SQLiteSettingRepository();
    }

    @AfterEach
    void tearDown() {

        DatabaseManager.resetDatabaseFile();
    }

    @Test
    void saveAndFindValue() {

        repository.save("theme", "dark");

        assertEquals(Optional.of("dark"), repository.findValue("theme"));
    }

    @Test
    void saveOverwritesExistingValue() {

        repository.save("theme", "dark");
        repository.save("theme", "light");

        assertEquals(Optional.of("light"), repository.findValue("theme"));
    }

    @Test
    void deleteRemovesValue() {

        repository.save("theme", "dark");

        repository.delete("theme");

        assertTrue(repository.findValue("theme").isEmpty());
    }

    @Test
    void findValueReturnsEmptyForUnknownKey() {

        assertTrue(repository.findValue("missing-key").isEmpty());
    }
}