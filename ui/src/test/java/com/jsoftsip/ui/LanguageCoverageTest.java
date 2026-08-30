package com.jsoftsip.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCoverageTest {

    @Test
    void spanishAndPortugueseCoverAllEnglishKeys() throws IOException {

        Properties english = loadProperties("/i18n/messages.properties");
        Properties spanish = loadProperties("/i18n/messages_es.properties");
        Properties portuguese = loadProperties("/i18n/messages_pt.properties");

        for (String key : english.stringPropertyNames()) {

            assertTrue(spanish.containsKey(key), "messages_es.properties must contain key: " + key);

            assertTrue(portuguese.containsKey(key), "messages_pt.properties must contain key: " + key);
        }
    }

    private Properties loadProperties(String resource) throws IOException {

        Properties properties = new Properties();

        try (InputStream stream = getClass().getResourceAsStream(resource)) {

            if (stream == null) {
                throw new IOException("Resource not found: " + resource);
            }

            properties.load(stream);
        }

        return properties;
    }
}
