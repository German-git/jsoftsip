package com.jsoftsip.core.config;

import java.nio.file.Path;

public final class ApplicationPaths {

    private ApplicationPaths() {
    }

    public static Path getConfigDirectory() {
        return ConfigDirectoryResolver.getConfigurationDirectory();
    }

    public static Path getDatabaseFile() {
        return getConfigDirectory().resolve("jsoftsip.db");
    }

    public static Path getMasterKeyFile() {
        return getConfigDirectory().resolve("master.key");
    }

    public static Path getMasterKeyBackupFile() {
        return getConfigDirectory().resolve("master.key.bak");
    }

    public static Path getBaresipDirectory() {
        return getConfigDirectory().resolve("baresip");
    }
}
