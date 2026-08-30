package com.jsoftsip.ui.dialog;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;

/**
 * Enumerates the visual strategies available for custom dialogs.
 *
 * <p>Each type carries an ikonli icon and a JavaFX color for that
 * icon, encapsulating the presentation logic so that callers never
 * branch on type. Using a {@link Color} instead of a CSS string
 * avoids overwriting the {@code -fx-font-family} style that ikonli
 * sets internally to render the glyph.</p>
 */
public enum DialogType {

    ERROR(BootstrapIcons.EXCLAMATION_OCTAGON_FILL, Color.web("#dc3545")),

    WARNING(BootstrapIcons.EXCLAMATION_TRIANGLE_FILL, Color.web("#fd7e14")),

    INFO(BootstrapIcons.INFO_CIRCLE_FILL, Color.web("#0d6efd")),

    SUCCESS(BootstrapIcons.CHECK_CIRCLE_FILL, Color.web("#198754"));

    private final Ikon icon;

    private final Color iconColor;

    DialogType(Ikon icon, Color iconColor) {

        this.icon = icon;
        this.iconColor = iconColor;
    }

    public Ikon getIcon() {

        return icon;
    }

    public Color getIconColor() {

        return iconColor;
    }
}
