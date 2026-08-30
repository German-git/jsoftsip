module com.jsoftsip.nativebridge {
    exports com.jsoftsip.nativebridge.baresip;
    exports com.jsoftsip.nativebridge.video;

    // Test classes share these packages and are patched in at test
    // runtime, so JUnit (org.junit.platform.commons and the Jupiter
    // engine) can reflect via Constructor.setAccessible
    opens com.jsoftsip.nativebridge.baresip;
    opens com.jsoftsip.nativebridge.video;

    requires com.jsoftsip.core;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;

    // JUnit is compile-scope + optional in the POM, so it is on the
    // module path during compilation but not at runtime. requires static
    // makes it optional at runtime, allowing tests to compile and run
    // (including from IntelliJ) without leaking JUnit into production or
    // native image builds.
    requires static org.junit.jupiter.api;
}