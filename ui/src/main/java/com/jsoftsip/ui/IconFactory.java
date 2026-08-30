package com.jsoftsip.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

public final class IconFactory {

    private IconFactory() {
    }

    public static void configureButton(Button button, Ikon ikon, String tooltipText) {

        FontIcon icon = new FontIcon(ikon);

        icon.setIconSize(16);

        button.setText(null);

        button.setGraphic(icon);

        button.setTooltip(new Tooltip(tooltipText));
    }

    public static void configureLabel(javafx.scene.control.Label label, Ikon ikon, String tooltipText) {

        FontIcon icon = new FontIcon(ikon);

        icon.setIconSize(16);

        label.setGraphic(icon);

        label.setTooltip(new Tooltip(tooltipText));
    }

    public static void configureSuccessButton(Button button, Ikon icon, String tooltipText) {

        configureButton(button, icon, tooltipText);

        button.getStyleClass().add("success-button");
    }

    public static void configureDangerButton(Button button, Ikon icon, String tooltipText) {

        configureButton(button, icon, tooltipText);

        button.getStyleClass().add("danger-button");
    }

    public static void configurePositiveButton(Button button, Ikon icon, String tooltipText) {

        configureButton(button, icon, tooltipText);

        button.getStyleClass().add("positive-button");
    }

    /**
     * Same as {@link #configureButton} but binds the tooltip text to the
     * given resource key so it updates automatically when the locale changes.
     */
    public static void configureI18nButton(Button button, Ikon ikon, String tooltipKey) {

        configureButton(button, ikon, I18n.get(tooltipKey));

        button.getTooltip().textProperty().bind(I18n.createStringBinding(tooltipKey));
    }

    /**
     * Same as {@link #configureLabel} but binds the tooltip text to the
     * given resource key so it updates automatically when the locale changes.
     */
    public static void configureI18nLabel(javafx.scene.control.Label label, Ikon ikon, String tooltipKey) {

        configureLabel(label, ikon, I18n.get(tooltipKey));

        label.getTooltip().textProperty().bind(I18n.createStringBinding(tooltipKey));
    }

    public static void configureI18nSuccessButton(Button button, Ikon icon, String tooltipKey) {

        configureI18nButton(button, icon, tooltipKey);

        button.getStyleClass().add("success-button");
    }

    public static void configureI18nDangerButton(Button button, Ikon icon, String tooltipKey) {

        configureI18nButton(button, icon, tooltipKey);

        button.getStyleClass().add("danger-button");
    }

    public static void configureI18nPositiveButton(Button button, Ikon icon, String tooltipKey) {

        configureI18nButton(button, icon, tooltipKey);

        button.getStyleClass().add("positive-button");
    }
}
