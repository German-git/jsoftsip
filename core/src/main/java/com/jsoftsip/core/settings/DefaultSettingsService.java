package com.jsoftsip.core.settings;

import java.util.Optional;

public class DefaultSettingsService implements SettingsService {

    private final SettingRepository repository;

    public DefaultSettingsService(SettingRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveSetting(String key, String value) {

        repository.save(key, value);
    }

    @Override
    public void deleteSetting(String key) {

        repository.delete(key);
    }

    @Override
    public Optional<String> getSetting(String key) {

        return repository.findValue(key);
    }
}