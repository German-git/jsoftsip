package com.jsoftsip.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nTest {

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @BeforeEach
    void resetLocale() throws Exception {

        runOnFxThread(() -> I18n.setLocale(Locale.of("en")));
    }

    @AfterEach
    void restoreLocale() throws Exception {

        runOnFxThread(() -> I18n.setLocale(Locale.of("en")));
    }

    @Test
    void getReturnsEnglishByDefault() {

        assertEquals("Phone number or SIP account", I18n.get("dialer.prompt"));
    }

    @Test
    void getReturnsSpanishAfterLocaleChange() throws Exception {

        runOnFxThread(() -> I18n.setLocale(Locale.of("es")));

        assertEquals("Número telefónico o cuenta SIP", I18n.get("dialer.prompt"));
    }

    @Test
    void englishResolvesToBaseBundleDespiteNonEnglishJvmDefault() {

        // Regression for the ResourceBundle pitfall: when the selected locale is
        // English but the JVM default locale is non-English, getBundle("...en")
        // falls back to the default locale (Spanish) instead of the base English
        // bundle. I18n must resolve English to the ROOT bundle regardless of the
        // JVM default. We clear the bundle cache and set the JVM default to
        // Spanish while the selected locale stays English (set by @BeforeEach).
        ResourceBundle.clearCache();

        Locale.setDefault(Locale.of("es"));

        assertEquals("Call History", I18n.get("history.title"));
        assertEquals("Accounts", I18n.get("accounts.title"));
    }

    @Test
    void stringBindingUpdatesWhenLocaleChanges() throws Exception {

        var binding = I18n.createStringBinding("dialer.prompt");

        assertEquals("Phone number or SIP account", binding.get());

        runOnFxThread(() -> I18n.setLocale(Locale.of("es")));

        assertEquals("Número telefónico o cuenta SIP", binding.get());

        runOnFxThread(() -> I18n.setLocale(Locale.of("en")));

        assertEquals("Phone number or SIP account", binding.get());
    }

    private void runOnFxThread(Runnable action) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {

            action.run();
            latch.countDown();
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        if (!completed) {
            throw new IllegalStateException("FX action did not complete within timeout");
        }
    }
}
