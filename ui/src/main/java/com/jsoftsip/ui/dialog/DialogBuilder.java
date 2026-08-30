package com.jsoftsip.ui.dialog;

import com.jsoftsip.ui.I18n;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link DialogSpec}.
 *
 * <p>Default configuration (matching the requirement that dialogs
 * default to error type): {@code type = ERROR}, {@code modal = true},
 * {@code owner = null}, and a single OK action resolved from i18n.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * DialogBuilder.error()
 *     .title("Call Failed")
 *     .content("Could not start the call. Please try again.")
 *     .owner(ownerWindow)
 *     .showAndWait(),
 * </pre>
 *
 * <p>For confirmation dialogs:</p>
 * <pre>
 * boolean confirmed = DialogBuilder.warning()
 *     .title("Restart Baresip")
 *     .content("All active calls will be dropped. Continue?")
 *     .okCancel()
 *     .owner(ownerWindow)
 *     .confirm(),
 * </pre>
 */
public class DialogBuilder {

    private DialogType type = DialogType.ERROR;

    private String title = "";

    private String header = null;

    private String content = "";

    private final List<DialogAction> actions = new ArrayList<>();

    private boolean modal = true;

    private Window owner = null;

    /**
     * Creates a new builder with no pre-set type.
     */
    public static DialogBuilder builder() {

        return new DialogBuilder();
    }

    /**
     * Creates a builder pre-configured for an error dialog.
     */
    public static DialogBuilder error() {

        return new DialogBuilder().type(DialogType.ERROR);
    }

    /**
     * Creates a builder pre-configured for a warning dialog.
     */
    public static DialogBuilder warning() {

        return new DialogBuilder().type(DialogType.WARNING);
    }

    /**
     * Creates a builder pre-configured for an information dialog.
     */
    public static DialogBuilder info() {

        return new DialogBuilder().type(DialogType.INFO);
    }

    /**
     * Creates a builder pre-configured for a success dialog.
     */
    public static DialogBuilder success() {

        return new DialogBuilder().type(DialogType.SUCCESS);
    }

    public DialogBuilder type(DialogType type) {

        this.type = type;

        return this;
    }

    public DialogBuilder title(String title) {

        this.title = title;

        return this;
    }

    public DialogBuilder header(String header) {

        this.header = header;

        return this;
    }

    public DialogBuilder content(String content) {

        this.content = content;

        return this;
    }

    public DialogBuilder modal(boolean modal) {

        this.modal = modal;

        return this;
    }

    public DialogBuilder owner(Window owner) {

        this.owner = owner;

        return this;
    }

    /**
     * Sets the dialog's actions (buttons). If none are provided and
     * build() is called, a single OK action is added automatically.
     */
    public DialogBuilder actions(DialogAction... actions) {

        this.actions.clear();

        for (DialogAction action : actions) {

            this.actions.add(action);
        }

        return this;
    }

    /**
     * Convenience: configures OK and Cancel buttons with i18n labels.
     * The OK action is returned as {@link javafx.util.Callback} result
     * {@code true} via {@link #confirm()}.
     */
    public DialogBuilder okCancel() {

        return actions(DialogAction.of(I18n.get("dialog.ok")), DialogAction.cancel(I18n.get("dialog.cancel")));
    }

    /**
     * Convenience: configures Yes and No buttons with i18n labels.
     */
    public DialogBuilder yesNo() {

        return actions(DialogAction.of(I18n.get("dialog.yes"), "success-button"),
                       DialogAction.of(I18n.get("dialog.no"), "secondary-button"));
    }

    /**
     * Convenience: configures a single OK button with i18n label.
     */
    public DialogBuilder ok() {

        return actions(DialogAction.of(I18n.get("dialog.ok")));
    }

    /**
     * Builds and returns the immutable spec.
     */
    public DialogSpec build() {

        if (actions.isEmpty()) {

            actions.add(DialogAction.of(I18n.get("dialog.ok")));
        }

        return new DialogSpec(type, title, header, content, actions, modal, owner);
    }

    /**
     * Builds the spec and shows a modal dialog, returning the
     * clicked action (or empty if the dialog was closed without
     * a click).
     */
    public java.util.Optional<DialogAction> showAndWait() {

        return DialogFactory.showAndWait(build());
    }

    /**
     * Builds the spec and shows a non-modal dialog.
     */
    public void show() {

        DialogFactory.show(build());
    }

    /**
     * Builds the spec with OK/Cancel actions and shows a modal dialog,
     * returning {@code true} when the user clicked OK.
     */
    public boolean confirm() {

        DialogAction ok = DialogAction.of(I18n.get("dialog.ok"));

        DialogAction cancel = DialogAction.cancel(I18n.get("dialog.cancel"));

        java.util.Optional<DialogAction> result = DialogFactory.showAndWait(actions(ok, cancel).build());

        // Identity comparison: the label is just
        // presentation - a locale switch or duplicate texts must
        // never decide which action fired.
        return result.isPresent() && result.get() == ok;
    }
}
