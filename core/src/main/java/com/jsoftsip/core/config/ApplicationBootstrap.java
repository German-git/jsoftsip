package com.jsoftsip.core.config;

import com.jsoftsip.core.infrastructure.crypto.MasterKeyManager;
import com.jsoftsip.core.infrastructure.sqlite.DatabaseInitializer;

public final class ApplicationBootstrap {

    private ApplicationBootstrap() {
    }

    public static void initialize() {

        DatabaseInitializer.initialize();

        MasterKeyManager.initialize();
    }
}