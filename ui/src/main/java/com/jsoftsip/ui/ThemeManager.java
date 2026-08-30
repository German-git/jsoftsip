package com.jsoftsip.ui;

import com.jsoftsip.core.logging.JSoftSipLog;
import javafx.application.Application;

import java.net.URL;
import java.util.Optional;

public final class ThemeManager {

    private static ThemeType currentTheme = ThemeType.PRIMER_DARK;

    private ThemeManager() {
    }

    public static void initialize() {

        applyTheme(currentTheme);
    }

    public static void applyTheme(ThemeType themeType) {

        currentTheme = themeType;

        Application.setUserAgentStylesheet(themeType.getTheme().getUserAgentStylesheet());
    }

    public static ThemeType getCurrentTheme() {

        return currentTheme;
    }

    /**
     * Returns the URL of the application-specific CSS, if the
     * resource is present. Callers should only add the stylesheet
     * when the optional is non-empty to avoid a null entry in the
     * JavaFX stylesheet list.
     */
    public static Optional<String> getApplicationStylesheet() {

        URL resource = ThemeManager.class.getResource("/css/app.css");

        if (resource == null) {

            JSoftSipLog.warn("Application stylesheet /css/app.css not found");

            return Optional.empty();
        }

        return Optional.of(resource.toExternalForm());
    }
}