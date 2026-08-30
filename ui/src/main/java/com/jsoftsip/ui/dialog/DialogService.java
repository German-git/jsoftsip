package com.jsoftsip.ui.dialog;

import javafx.stage.Window;

import java.util.Optional;

/**
 * High-level convenience API over {@link DialogBuilder} and
 * {@link DialogFactory}. Provides single-line methods for the
 * most common dialog patterns: error/info/warning/success alerts
 * and boolean confirmations.
 *
 * <p>All methods accept an optional owner {@link Window} for correct
 * anchoring and z-order. When the owner is {@code null} the dialog
 * is centered on screen.</p>
 *
 * <p>Every method defaults to {@link DialogType#ERROR} when the type
 * is not meaningful (e.g. the generic alert methods take an explicit
 * type via the builder). The {@code confirm} overload uses WARNING
 * by default to signal a destructive action.</p>
 */
public final class DialogService {

    private DialogService() {
    }

    /**
     * Shows a modal error dialog with a single OK button.
     */
    public static void showError(Window owner, String title, String content) {

        DialogBuilder.error().title(title).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal error dialog with a header and content.
     */
    public static void showError(Window owner, String title, String header, String content) {

        DialogBuilder.error().title(title).header(header).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal information dialog with a single OK button.
     */
    public static void showInfo(Window owner, String title, String content) {

        DialogBuilder.info().title(title).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal information dialog with a header and content.
     */
    public static void showInfo(Window owner, String title, String header, String content) {

        DialogBuilder.info().title(title).header(header).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal warning dialog with a single OK button.
     */
    public static void showWarning(Window owner, String title, String content) {

        DialogBuilder.warning().title(title).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal warning dialog with a header and content.
     */
    public static void showWarning(Window owner, String title, String header, String content) {

        DialogBuilder.warning().title(title).header(header).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal success dialog with a single OK button.
     */
    public static void showSuccess(Window owner, String title, String content) {

        DialogBuilder.success().title(title).content(content).owner(owner).showAndWait();
    }

    /**
     * Shows a modal confirmation dialog (warning type) with OK and
     * Cancel buttons. Returns {@code true} when the user clicks OK.
     */
    public static boolean confirm(Window owner, String title, String content) {

        return DialogBuilder.warning().title(title).content(content).owner(owner).confirm();
    }

    /**
     * Shows a modal confirmation dialog (warning type) with a header
     * and content. Returns {@code true} when the user clicks OK.
     */
    public static boolean confirm(Window owner, String title, String header, String content) {

        return DialogBuilder.warning().title(title).header(header).content(content).owner(owner).confirm();
    }

    /**
     * Shows a fully custom dialog from a builder and returns the
     * clicked action (empty if closed without clicking).
     */
    public static Optional<DialogAction> showAndWait(DialogBuilder builder) {

        return builder.showAndWait();
    }
}
