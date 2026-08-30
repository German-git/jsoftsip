package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.ui.ThemeManager;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory that materializes a {@link DialogSpec} into a
 * {@link javafx.scene.control.Dialog Dialog&lt,DialogAction&gt,}}.
 *
 * <p>The factory owns all JavaFX construction logic — the custom
 * layout (icon | header/content), button wiring, result conversion,
 * and theme stylesheet attachment. Callers interact only with
 * {@link DialogBuilder} or {@link DialogService}.</p>
 *
 * <p>Key invariants:</p>
 * <ul>
 *   <li>Every dialog defaults to {@code resizable=false} and
 *       content-width-bounded so messages never create an
 *       oversized window.</li>
 *   <li>The app stylesheet ({@link ThemeManager#getApplicationStylesheet()})
 *       is always attached so dialogs inherit the current theme.</li>
 *   <li>Button style classes (e.g. {@code "danger-button"}) are
 *       applied after the dialog is shown, when the platform has
 *       created the button nodes.</li>
 * </ul>
 */
public final class DialogFactory {

    private static final double MAX_DIALOG_WIDTH = 420;

    private static final double MIN_DIALOG_WIDTH = 368;

    private static final double MIN_DIALOG_HEIGHT = 144;

    private static final int ICON_SIZE = 24;

    private static final String ICON_RESOURCE = "/com/jsoftsip/ui/icons/jsoftsip-icon-64px.png";

    private static final Image APP_ICON = loadAppIcon();

    private DialogFactory() {
    }

    private static Image loadAppIcon() {

        try {

            return new Image(DialogFactory.class.getResourceAsStream(ICON_RESOURCE));

        } catch (Exception exception) {

            // Fallback with WARN per the exception policy
            // (docs/exceptions.md, rule 1): a missing icon must not
            // block dialog construction, but the gap stays visible.
            JSoftSipLog.warn("Application icon could not be loaded, dialogs will use the platform default", exception);

            return null;
        }
    }

    /**
     * Creates a fully configured {@link Dialog} from the given spec.
     * The caller is responsible for showing it (modal or not).
     */
    public static Dialog<DialogAction> createDialog(DialogSpec spec) {

        Dialog<DialogAction> dialog = new Dialog<>();

        dialog.setTitle(spec.title());

        if (spec.owner() != null) {

            dialog.initOwner(spec.owner());
        }

        if (spec.modal()) {

            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        Map<ButtonType, DialogAction> actionMap = new HashMap<>();

        List<ButtonType> buttonTypes = new ArrayList<>();

        for (DialogAction action : spec.actions()) {

            // The button bar role comes from the
            // stable action kind, not from label text. CANCEL_CLOSE
            // makes Escape resolve the cancel action and keeps Enter
            // on the first OK_DONE button.
            ButtonBar.ButtonData data = action.kind() == DialogAction.Kind.CANCEL
                ? ButtonBar.ButtonData.CANCEL_CLOSE
                : ButtonBar.ButtonData.OK_DONE;

            ButtonType bt = new ButtonType(action.text(), data);

            buttonTypes.add(bt);

            actionMap.put(bt, action);
        }

        dialog.getDialogPane().setHeader(null);
        dialog.getDialogPane().setGraphic(null);
        dialog.getDialogPane().setContent(createContent(spec));
        dialog.getDialogPane().getButtonTypes().setAll(buttonTypes);

        ThemeManager.getApplicationStylesheet().ifPresent(css -> dialog.getDialogPane().getStylesheets().add(css));

        dialog.setResultConverter(actionMap::get);

        dialog.setResizable(false);

        dialog.getDialogPane().setMinWidth(MIN_DIALOG_WIDTH);
        dialog.getDialogPane().setMinHeight(MIN_DIALOG_HEIGHT);

        // Apply per-action style classes and the window icon after the
        // platform creates the button nodes (lookupButton returns null
        // before show) and the stage/window is available.
        dialog.setOnShown(event -> {

            applyButtonStyles(dialog.getDialogPane(), actionMap);

            applyWindowIcon(dialog);
        });

        return dialog;
    }

    /**
     * Builds and shows a modal dialog, returning the action the
     * user chose (empty if the dialog was closed without clicking).
     */
    public static Optional<DialogAction> showAndWait(DialogSpec spec) {

        Dialog<DialogAction> dialog = createDialog(spec);

        return dialog.showAndWait();
    }

    /**
     * Builds and shows a non-modal dialog.
     */
    public static void show(DialogSpec spec) {

        Dialog<DialogAction> dialog = createDialog(spec);

        dialog.show();
    }

    /**
     * Constructs the left-icon / right-content layout for the dialog.
     * The icon size (24px) is larger than toolbar icons (16px) for
     * visibility at message scale.
     */
    private static Node createContent(DialogSpec spec) {

        FontIcon icon = new FontIcon(spec.type().getIcon());

        icon.setIconSize(ICON_SIZE);

        icon.setIconColor(spec.type().getIconColor());

        StackPane iconContainer = new StackPane(icon);

        iconContainer.setMinSize(ICON_SIZE, ICON_SIZE);

        iconContainer.setPrefSize(ICON_SIZE, ICON_SIZE);

        iconContainer.setMaxSize(ICON_SIZE, ICON_SIZE);

        VBox messageBox = new VBox(8);

        String header = spec.header();

        if (header != null && !header.isEmpty()) {

            Label headerLabel = new Label(header);

            headerLabel.getStyleClass().add("dialog-header");

            headerLabel.setWrapText(true);

            messageBox.getChildren().add(headerLabel);
        }

        Label contentLabel = new Label(spec.content());

        contentLabel.setWrapText(true);

        VBox.setVgrow(contentLabel, Priority.ALWAYS);

        messageBox.getChildren().add(contentLabel);

        HBox root = new HBox(16, iconContainer, messageBox);

        root.setStyle("-fx-max-width: " + MAX_DIALOG_WIDTH + "px;");

        HBox.setMargin(iconContainer, new Insets(8, 0, 8, 0));

        return root;
    }

    /**
     * Adds the JSoftSIP application icon to the dialog's owning
     * stage once the window is realized. Silently no-ops if the icon
     * cannot be loaded or the stage is not available.
     */
    private static void applyWindowIcon(Dialog<DialogAction> dialog) {

        if (APP_ICON == null || APP_ICON.isError()) {

            return;
        }

        javafx.stage.Window window = dialog.getDialogPane().getScene().getWindow();

        if (window instanceof javafx.stage.Stage stage) {

            if (stage.getIcons().isEmpty()) {

                stage.getIcons().add(APP_ICON);
            }
        }
    }

    /**
     * Walks the dialog pane's button types and applies the style
     * class declared on each DialogAction. Called from setOnShown
     * so the button nodes exist.
     */
    private static void applyButtonStyles(javafx.scene.control.DialogPane pane,
                                          Map<ButtonType, DialogAction> actionMap) {

        for (Map.Entry<ButtonType, DialogAction> entry : actionMap.entrySet()) {

            ButtonType bt = entry.getKey();

            DialogAction action = entry.getValue();

            if (action.styleClass() == null) {

                continue;
            }

            Button button = (Button) pane.lookupButton(bt);

            if (button != null) {

                button.getStyleClass().add(action.styleClass());
            }
        }
    }
}
