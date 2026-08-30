package com.jsoftsip.ui;

import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;

public class UiPreferencesService {

    private static final String THEME_KEY = "selected_theme";

    private final SettingsService settingsService;

    public UiPreferencesService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    private boolean getBoolean(String key, String defaultValue) {

        return Boolean.parseBoolean(settingsService.getSetting(key).orElse(defaultValue));
    }

    public ThemeType getTheme() {

        return settingsService.getSetting(THEME_KEY).flatMap(themeName -> {
            try {
                return java.util.Optional.of(ThemeType.valueOf(themeName));
            } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
            }
        }).orElse(ThemeType.PRIMER_DARK);
    }

    public void saveTheme(ThemeType theme) {

        settingsService.saveSetting(THEME_KEY, theme.name());
    }

    public boolean isSaveBaresipLogToFile() {

        return getBoolean(SettingsKeys.UI_LOGGING_SAVE_TO_FILE, SettingsKeys.UI_LOGGING_SAVE_TO_FILE_DEFAULT);
    }

    public void saveBaresipLogToFile(boolean enabled) {

        settingsService.saveSetting(SettingsKeys.UI_LOGGING_SAVE_TO_FILE, Boolean.toString(enabled));
    }

    public boolean isRememberWindowGeometry() {

        return getBoolean(SettingsKeys.UI_WINDOW_REMEMBER_GEOMETRY, SettingsKeys.UI_WINDOW_REMEMBER_GEOMETRY_DEFAULT);
    }

    public void saveRememberWindowGeometry(boolean remember) {

        settingsService.saveSetting(SettingsKeys.UI_WINDOW_REMEMBER_GEOMETRY, Boolean.toString(remember));
    }

    /**
     * Raw serialized geometry (x,y,width,height). Parsing
     * and validation live in the javafx-free WindowGeometry
     * value object, not here.
     */
    public java.util.Optional<String> getWindowGeometry() {

        return settingsService.getSetting(SettingsKeys.UI_WINDOW_GEOMETRY);
    }

    public void saveWindowGeometry(String serialized) {

        settingsService.saveSetting(SettingsKeys.UI_WINDOW_GEOMETRY, serialized);
    }

    public boolean isConfirmExitWithCalls() {

        return getBoolean(SettingsKeys.UI_CONFIRM_EXIT_WITH_CALLS, SettingsKeys.UI_CONFIRM_EXIT_WITH_CALLS_DEFAULT);
    }

    public void saveConfirmExitWithCalls(boolean confirm) {

        settingsService.saveSetting(SettingsKeys.UI_CONFIRM_EXIT_WITH_CALLS, Boolean.toString(confirm));
    }

    public Language getLanguage() {

        return Language.fromName(settingsService.getSetting(SettingsKeys.UI_LANGUAGE)
                                                .orElse(SettingsKeys.UI_LANGUAGE_DEFAULT));
    }

    public void saveLanguage(Language language) {

        settingsService.saveSetting(SettingsKeys.UI_LANGUAGE, language.name());
    }
}
