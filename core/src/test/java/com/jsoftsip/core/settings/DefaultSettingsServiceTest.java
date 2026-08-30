package com.jsoftsip.core.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSettingsServiceTest {

    private InMemorySettingRepository repository;

    private DefaultSettingsService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySettingRepository();
        service = new DefaultSettingsService(repository);
    }

    @Test
    void saveThenGetReturnsPersistedValue() {
        service.saveSetting("theme", "primer_dark");

        assertEquals(Optional.of("primer_dark"), service.getSetting("theme"));
    }

    @Test
    void deleteRemovesPersistedValue() {
        service.saveSetting("theme", "primer_dark");
        service.deleteSetting("theme");

        assertTrue(service.getSetting("theme").isEmpty());
    }

    @Test
    void getMissingReturnsEmpty() {
        assertTrue(service.getSetting("does-not-exist").isEmpty());
    }

    static class InMemorySettingRepository implements SettingRepository {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public void save(String key, String value) {
            store.put(key, value);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public Optional<String> findValue(String key) {
            return Optional.ofNullable(store.get(key));
        }
    }
}
