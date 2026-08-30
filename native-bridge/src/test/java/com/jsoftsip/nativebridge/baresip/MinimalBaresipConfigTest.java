package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimalBaresipConfigTest {

    private String originalModulePath;

    @AfterEach
    void restoreSystemProperty() {

        if (originalModulePath == null) {

            System.clearProperty(MinimalBaresipConfig.MODULE_PATH_PROPERTY);

        } else {

            System.setProperty(MinimalBaresipConfig.MODULE_PATH_PROPERTY, originalModulePath);
        }
    }

    @Test
    void rendersExpectedMinimalContentWithDefaultModulePath() {

        List<String> lines = MinimalBaresipConfig.lines("127.0.0.1", 4444);

        assertEquals(List.of("# Minimal JSoftSIP config: ctrl_tcp only", "# Accounts are provisioned at runtime via",
                             "# ctrl_tcp (uanew), never via an accounts file.", "",
                             "module_path\t\t/usr/lib/baresip/modules", "module_app\t\tctrl_tcp.so",
                             "ctrl_tcp_listen\t\t127.0.0.1:4444"),
                     lines);
    }

    @Test
    void ctrlTcpListenUsesGivenHostAndPort() {

        List<String> lines = MinimalBaresipConfig.lines("0.0.0.0", 9999);

        assertTrue(lines.contains("ctrl_tcp_listen\t\t0.0.0.0:9999"),
                   "listen line must render the given host and port");
    }

    @Test
    void loadsCtrlTcpModuleOnly() {

        List<String> lines = MinimalBaresipConfig.lines("127.0.0.1", 4444);

        assertTrue(lines.contains("module_app\t\tctrl_tcp.so"), "ctrl_tcp must be loaded as an application module");
        assertTrue(lines.contains("module_path\t\t/usr/lib/baresip/modules"),
                   "the module path must point at the system modules");
    }

    @Test
    void usesExplicitModulePath() {

        List<String> lines = MinimalBaresipConfig.lines("127.0.0.1", 4444, "/opt/baresip/modules");

        assertTrue(lines.contains("module_path\t\t/opt/baresip/modules"), "explicit module path must be rendered");
    }

    @Test
    void systemPropertyOverridesDefaultModulePath() {

        originalModulePath = System.getProperty(MinimalBaresipConfig.MODULE_PATH_PROPERTY);

        System.setProperty(MinimalBaresipConfig.MODULE_PATH_PROPERTY, "/custom/baresip/modules");

        List<String> lines = MinimalBaresipConfig.lines("127.0.0.1", 4444);

        assertTrue(lines.contains("module_path\t\t/custom/baresip/modules"),
                   "system property must override the default module path");
    }
}
