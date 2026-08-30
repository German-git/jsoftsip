package com.jsoftsip.ui;

import java.util.Locale;

/**
 * Supported application languages. Each value maps to a JavaFX locale
 * and a matching {@code i18n/messages_<locale>.properties} resource
 * bundle.
 */
public enum Language {

    ENGLISH(Locale.of("en")), SPANISH(Locale.of("es")), PORTUGUESE(Locale.of("pt"));

    private final Locale locale;

    Language(Locale locale) {

        this.locale = locale;
    }

    public Locale getLocale() {

        return locale;
    }

    /**
     * Resolves a language from its persisted name, falling back to
     * English when the stored value is unknown.
     */
    public static Language fromName(String name) {

        if (name == null || name.isBlank()) {
            return ENGLISH;
        }

        for (Language language : values()) {

            if (language.name().equalsIgnoreCase(name)) {
                return language;
            }
        }

        return ENGLISH;
    }
}
