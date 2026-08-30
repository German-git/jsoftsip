package com.jsoftsip.nativebridge.baresip;

import java.util.List;

/**
 * Builds the minimal Baresip config needed to run headless and
 * expose ctrl_tcp. The syntax mirrors the working manual config
 * in ~/.baresip/config: module_app loads application modules and
 * ctrl_tcp_listen takes "host:port" separated by whitespace.
 * Everything else (audio/video drivers, codecs, call settings)
 * is deliberately left out: accounts are provisioned via ctrl_tcp
 * and media settings are out of scope for the SIP registration
 * flow.
 *
 * <p>This is the single source of the minimal fallback: the
 * launcher writes it on first launch and the config service uses
 * it as patch base and restore source, so both paths can never
 * drift apart.
 */
public final class MinimalBaresipConfig {

    /**
     * System property that overrides the default baresip module path.
     * Useful when baresip is installed in a non-standard location.
     */
    public static final String MODULE_PATH_PROPERTY = "jsoftsip.baresip.module.path";

    private static final String DEFAULT_MODULE_PATH = "/usr/lib/baresip/modules";

    private MinimalBaresipConfig() {
    }

    /**
     * Returns the minimal config lines for the given ctrl_tcp
     * endpoint using the configured module path.
     * <p>The module path is resolved from the system property
     * {@value #MODULE_PATH_PROPERTY}, falling back to
     * {@value #DEFAULT_MODULE_PATH}.
     */
    public static List<String> lines(String ctrlTcpHost, int ctrlTcpPort) {

        return lines(ctrlTcpHost, ctrlTcpPort, resolveModulePath());
    }

    /**
     * Returns the minimal config lines for the given ctrl_tcp
     * endpoint and an explicit module path.
     */
    public static List<String> lines(String ctrlTcpHost, int ctrlTcpPort, String modulePath) {

        return List.of("# Minimal JSoftSIP config: ctrl_tcp only", "# Accounts are provisioned at runtime via",
                       "# ctrl_tcp (uanew), never via an accounts file.", "", "module_path\t\t" + modulePath,
                       "module_app\t\tctrl_tcp.so", "ctrl_tcp_listen\t\t" + ctrlTcpHost + ":" + ctrlTcpPort);
    }

    private static String resolveModulePath() {
        return System.getProperty(MODULE_PATH_PROPERTY, DEFAULT_MODULE_PATH);
    }
}
