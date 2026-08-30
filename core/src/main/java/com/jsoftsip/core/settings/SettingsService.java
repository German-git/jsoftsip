package com.jsoftsip.core.settings;

import java.util.Optional;

public interface SettingsService {

    void saveSetting(String key, String value);

    void deleteSetting(String key);

    Optional<String> getSetting(String key);
}