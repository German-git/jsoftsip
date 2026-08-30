package com.jsoftsip.core.settings;

import java.util.Optional;

public interface SettingRepository {

    void save(String key, String value);

    void delete(String key);

    Optional<String> findValue(String key);
}