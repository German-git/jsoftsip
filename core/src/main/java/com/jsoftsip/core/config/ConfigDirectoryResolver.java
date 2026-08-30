package com.jsoftsip.core.config;

import java.nio.file.Path;

public final class ConfigDirectoryResolver {

    private static final String APPLICATION_NAME = "jsoftsip";

    /**
     * System property that overrides the configuration directory for testing
     * or custom deployments. When absent, the resolver falls back to the
     * platform-specific directories (APPDATA on Windows, XDG_CONFIG_HOME on
     * POSIX, or ~/.config).
     */
    public static final String CONFIG_DIR_PROPERTY = "jsoftsip.config.dir";

    private ConfigDirectoryResolver() {
    }

    public static Path getConfigurationDirectory() {

        String override = System.getProperty(CONFIG_DIR_PROPERTY);

        if (override != null && !override.isBlank()) {

            return Path.of(override);
        }

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {

            String appData = System.getenv("APPDATA");

            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, APPLICATION_NAME);
            }

            // APPDATA can be missing in minimal Windows environments (containers,
            // CI, sandboxes). Fall back to the user home directory so startup does
            // not crash on a NullPointerException.
            return Path.of(System.getProperty("user.home"), APPLICATION_NAME);
        }

        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");

        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return Path.of(xdgConfigHome, APPLICATION_NAME);
        }

        return Path.of(System.getProperty("user.home"), ".config", APPLICATION_NAME);
    }
}
