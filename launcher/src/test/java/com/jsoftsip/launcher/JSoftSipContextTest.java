package com.jsoftsip.launcher;

import com.jsoftsip.core.config.ConfigDirectoryResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock-backend composition root: the video frame source must be
 * empty because no native transport exists, mirroring the
 * baresip settings facade behavior.
 *
 * <p>This test mutates the backend system property, so it is forced to run
 * on a single thread to avoid interfering with other tests that may read it
 * in parallel.</p>
 */
@Execution(ExecutionMode.SAME_THREAD)
class JSoftSipContextTest {

    @TempDir
    Path tempConfig;

    private String originalConfigDir;

    private String originalBackend;

    @BeforeEach
    void setUp() {

        originalConfigDir = System.getProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        originalBackend = System.getProperty(SipBackend.SYSTEM_PROPERTY);

        System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, tempConfig.toString());

        System.setProperty(SipBackend.SYSTEM_PROPERTY, "mock");
    }

    @AfterEach
    void tearDown() {

        if (originalConfigDir == null) {

            System.clearProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY);

        } else {

            System.setProperty(ConfigDirectoryResolver.CONFIG_DIR_PROPERTY, originalConfigDir);
        }

        if (originalBackend == null) {

            System.clearProperty(SipBackend.SYSTEM_PROPERTY);

        } else {

            System.setProperty(SipBackend.SYSTEM_PROPERTY, originalBackend);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void mockBackendExposesEmptyVideoFrameSource() {

        JSoftSipContext context = new JSoftSipContext();

        try {

            assertTrue(context.getVideoFrameSource(7).isEmpty(), "the mock backend must expose no video source");

        } finally {

            context.shutdown();
        }
    }
}
