package com.jsoftsip.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mutates the global configuration directory system property, so
 * it is forced to run on a single thread to avoid interfering
 * with other tests that may read it in parallel.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ConfigDirectoryResolverTest {

    @TempDir
    Path tempConfig;

    private String originalConfigDir;

    @BeforeEach
    void setUp() {

        originalConfigDir = System.getProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);
    }

    @AfterEach
    void tearDown() {

        if (originalConfigDir == null) {

            System.clearProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        } else {

            System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, originalConfigDir);
        }
    }

    @Test
    void overridePropertyWinsOverThePlatformDefault() {

        System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, tempConfig.toString());

        assertEquals(tempConfig, ConfigDirectoryResolver.getConfigurationDirectory(),
                     "the jsoftsip.config.dir property must redirect the configuration directory");
    }

    @Test
    void blankOverrideFallsBackToThePlatformDefault() {

        System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, "   ");

        Path resolved = ConfigDirectoryResolver.getConfigurationDirectory();

        assertEquals("jsoftsip", resolved.getFileName().toString(),
                     "without an effective override the directory must end in the application name");
    }

    @Test
    void missingOverrideFallsBackToThePlatformDefault() {

        System.clearProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        Path resolved = ConfigDirectoryResolver.getConfigurationDirectory();

        assertEquals("jsoftsip", resolved.getFileName().toString(),
                     "the platform default must still be application-scoped");
    }
}
