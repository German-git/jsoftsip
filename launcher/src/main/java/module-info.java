module com.jsoftsip.launcher {

    requires javafx.controls;
    requires javafx.fxml;

    requires com.jsoftsip.core;
    requires com.jsoftsip.ui;
    requires com.jsoftsip.nativebridge;

    exports com.jsoftsip.launcher;

    // Allow JUnit to instantiate test classes via reflection
    opens com.jsoftsip.launcher;

    // JUnit is compile-scope + optional in the POM so it is on the module
    // path for compilation and tests, but not at runtime. requires static
    // makes it optional at runtime, avoiding a production dependency on JUnit.
    requires static org.junit.jupiter.api;
}
