package com.jsoftsip.ui;

import com.jsoftsip.core.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiPreferencesServiceTest {

    private final MapSettingsService settingsService = new MapSettingsService();
    private final UiPreferencesService preferences = new UiPreferencesService(settingsService);

    @Test
    void returnsSavedThemeWhenValid() {
        settingsService.saveSetting("selected_theme", "DRACULA");

        assertEquals(ThemeType.DRACULA, preferences.getTheme());
    }

    @Test
    void returnsDefaultThemeWhenNoThemeSaved() {
        assertEquals(ThemeType.PRIMER_DARK, preferences.getTheme());
    }

    @Test
    void returnsDefaultThemeWhenSavedThemeIsInvalid() {
        settingsService.saveSetting("selected_theme", "INVALID_THEME_NAME");

        assertEquals(ThemeType.PRIMER_DARK, preferences.getTheme());
    }

    private static final class MapSettingsService implements SettingsService {

        private final Map<String, String> settings = new HashMap<>();

        @Override
        public void saveSetting(String key, String value) {
            settings.put(key, value);
        }

        @Override
        public void deleteSetting(String key) {
            settings.remove(key);
        }

        @Override
        public Optional<String> getSetting(String key) {
            return Optional.ofNullable(settings.get(key));
        }
    }
}
