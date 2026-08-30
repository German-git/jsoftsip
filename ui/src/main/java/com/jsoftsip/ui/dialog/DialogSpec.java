package com.jsoftsip.ui.dialog;

import javafx.stage.Window;

import java.util.List;

/**
 * Immutable specification of a custom dialog: the visual type (icon
 * and color), header, content, detail, available actions, modality,
 * and owning window.
 *
 * <p>Constructed exclusively through {@link DialogBuilder}. The
 * factory {@link DialogFactory} consumes this spec to build a
 * {@link javafx.scene.control.Dialog}.</p>
 */
public final class DialogSpec {

    private final DialogType type;

    private final String title;

    private final String header;

    private final String content;

    private final List<DialogAction> actions;

    private final boolean modal;

    private final Window owner;

    DialogSpec(DialogType type, String title, String header, String content, List<DialogAction> actions, boolean modal,
               Window owner) {

        this.type = type;
        this.title = title;
        this.header = header;
        this.content = content;
        this.actions = List.copyOf(actions);
        this.modal = modal;
        this.owner = owner;
    }

    public DialogType type() {

        return type;
    }

    public String title() {

        return title;
    }

    public String header() {

        return header;
    }

    public String content() {

        return content;
    }

    public List<DialogAction> actions() {

        return actions;
    }

    public boolean modal() {

        return modal;
    }

    public Window owner() {

        return owner;
    }
}
