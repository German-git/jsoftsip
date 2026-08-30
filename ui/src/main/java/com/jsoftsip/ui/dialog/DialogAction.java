package com.jsoftsip.ui.dialog;

/**
 * Represents a button in a custom dialog: its label text, an optional
 * style class (e.g. {@code "success-button"}), and the stable role
 * the factory and result consumers resolve by.
 *
 * <p>The action callback is intentionally not stored here — the caller
 * inspects which {@code DialogAction} was returned by
 * {@link javafx.scene.control.Dialog#showAndWait()} and dispatches
 * accordingly. This keeps the record a pure value object, easy to
 * test and free of lifecycle concerns.</p>
 *
 * <p>The {@link #kind()} role exists because translated labels are
 * presentation, not identity: button mapping in the factory and
 * result resolution after showAndWait compare roles or references,
 * never text.</p>
 */
public record DialogAction(String text, String styleClass, Kind kind) {

    /**
     * Stable semantic role of a dialog button.
     */
    public enum Kind {
        ACTION, CANCEL
    }

    /**
     * Creates an action with the given label and no extra styling.
     */
    public static DialogAction of(String text) {

        return new DialogAction(text, null, Kind.ACTION);
    }

    /**
     * Creates an action with the given label and CSS style class,
     * allowing the caller to apply the app's existing button styles
     * (e.g. {@code "danger-button"}, {@code "success-button"}).
     */
    public static DialogAction of(String text, String styleClass) {

        return new DialogAction(text, styleClass, Kind.ACTION);
    }

    /**
     * Creates the semantically-cancelling action: rendered with the
     * secondary style unless overridden, mapped to CANCEL_CLOSE by
     * the factory so Escape resolves it.
     */
    public static DialogAction cancel(String text) {

        return new DialogAction(text, "secondary-button", Kind.CANCEL);
    }
}
