package com.jsoftsip.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Central access point for the UI resource bundle. The default bundle is
 * English, locale-specific translations can be added by providing a matching
 * {@code i18n/messages_<locale>.properties} file. JavaFX FXML loaders and
 * controllers use this class to externalize all user-visible strings.
 *
 * <p>The locale is exposed as an observable property so that controllers can
 * bind labels, buttons, and other text to a key and have them update
 * automatically when the user changes the language at runtime.
 */
public final class I18n {

    private static final ObjectProperty<Locale> LOCALE = new SimpleObjectProperty<>(Locale.getDefault());

    static {
        LOCALE.addListener((observable, oldValue, newValue) -> Locale.setDefault(newValue));
    }

    private I18n() {
    }

    /**
     * Returns the resource bundle for the current locale. FXML loaders use
     * this for initial text resolution, runtime language changes require
     * bindings or FXML reload.
     */
    public static ResourceBundle bundle() {

        return ResourceBundle.getBundle("i18n.messages", bundleLocale());
    }

    /**
     * Returns the current locale used to load resource bundles.
     */
    public static Locale getLocale() {

        return LOCALE.get();
    }

    /**
     * Observable property for the current locale. Changing this property reloads
     * the resource bundle and invalidates all bindings created through this
     * class.
     */
    public static ObjectProperty<Locale> localeProperty() {

        return LOCALE;
    }

    /**
     * Sets the locale used for all future resource lookups and bindings.
     * This call is safe from any thread, the JavaFX property notification is
     * coalesced on the FX thread if necessary.
     */
    public static void setLocale(Locale locale) {

        if (locale == null) {
            throw new IllegalArgumentException("Locale cannot be null");
        }

        if (Platform.isFxApplicationThread()) {

            LOCALE.set(locale);

        } else {

            Platform.runLater(() -> LOCALE.set(locale));
        }
    }

    /**
     * Returns the string for the given key in the current locale.
     */
    public static String get(String key) {

        return ResourceBundle.getBundle("i18n.messages", bundleLocale()).getString(key);
    }

    /**
     * Maps the selected locale to the locale used for bundle lookup. English is
     * resolved to the {@link Locale#ROOT} base bundle so that switching to
     * English does not fall back to the JVM default locale (which may be a
     * previously selected non-English language) when no {@code messages_en}
     * file exists.
     */
    private static Locale bundleLocale() {

        return "en".equals(LOCALE.get().getLanguage()) ? Locale.ROOT : LOCALE.get();
    }

    /**
     * Returns a binding that tracks the current locale and re-resolves the
     * key whenever the locale changes. Bindings are lazy and only recompute
     * when observed.
     */
    public static StringBinding createStringBinding(String key) {

        return Bindings.createStringBinding(() -> get(key), LOCALE);
    }

    /**
     * Binds the given string property to the value of the key, so the property
     * updates automatically when the locale changes.
     */
    public static void bind(javafx.beans.property.StringProperty property, String key) {

        property.bind(createStringBinding(key));
    }

    /**
     * Formats a parameterized message using {@link MessageFormat}.
     * Use this when the value contains placeholders such as {@code {0}}.
     */
    public static String format(String key, Object... arguments) {

        return MessageFormat.format(get(key), arguments);
    }

    /**
     * Returns a binding that formats the message with the given arguments and
     * re-evaluates when the locale changes.
     */
    public static StringBinding formatBinding(String key, Object... arguments) {

        return Bindings.createStringBinding(() -> MessageFormat.format(get(key), arguments), LOCALE);
    }
}
