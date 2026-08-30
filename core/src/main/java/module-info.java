module com.jsoftsip.core {

    requires java.sql;
    requires org.slf4j;

    exports com.jsoftsip.core.account;
    exports com.jsoftsip.core.config;
    exports com.jsoftsip.core.crypto;
    exports com.jsoftsip.core.service;
    exports com.jsoftsip.core.settings;
    exports com.jsoftsip.core.settings.baresip;
    exports com.jsoftsip.core.call;
    exports com.jsoftsip.core.history;
    exports com.jsoftsip.core.registration;
    exports com.jsoftsip.core.infrastructure;
    exports com.jsoftsip.core.sip;
    exports com.jsoftsip.core.video;
    exports com.jsoftsip.core.logging;
    exports com.jsoftsip.core.util;
}