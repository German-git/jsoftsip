module com.jsoftsip.ui {

    requires javafx.controls;
    requires javafx.fxml;

    requires atlantafx.base;

    requires com.jsoftsip.core;

    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.bootstrapicons;

    exports com.jsoftsip.ui;
    exports com.jsoftsip.ui.controller;
    exports com.jsoftsip.ui.dialog;
    exports com.jsoftsip.ui.cell;
    exports com.jsoftsip.ui.window;

    opens com.jsoftsip.ui.controller to javafx.fxml;
}