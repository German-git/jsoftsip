package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.ControllerFactory;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.SceneFactory;
import com.jsoftsip.ui.controller.VideoCallDialogController;
import com.jsoftsip.ui.window.MainWindowRegistry;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Opens the per-call video dialog as a modal window owned by
 * the main stage. The dialog embeds a {@code VideoView}
 * for the remote preview and a TX toggle that maps to the
 * {@code videodir} baresip control command. One dialog exists
 * per call: a second request focuses the tracked window.
 */
public final class VideoCallDialogFactory {

    private VideoCallDialogFactory() {
    }

    public static void open(AppContext context, CallLeg call) {

        // Nothing prevented stacking several
        // dialogs for the same call, reuse and focus instead.
        if (VideoDialogManager.isOpen(call.getId())) {

            VideoDialogManager.focus(call.getId());

            return;
        }

        try {

            FXMLLoader loader = new FXMLLoader(VideoCallDialogFactory.class.getResource("/fxml/VideoCallDialog.fxml"));

            loader.setResources(I18n.bundle());
            loader.setControllerFactory(new ControllerFactory(context));

            Scene scene = SceneFactory.create(loader.load());

            VideoCallDialogController controller = loader.getController();

            controller.setCall(call);

            Stage stage = new Stage();

            // Bound so a locale change re-translates the title
            stage.titleProperty().bind(I18n.formatBinding("video.dialog.title", call.getDestination()));

            stage.initModality(Modality.WINDOW_MODAL);

            // WINDOW_MODAL needs an owner to block the right
            // window chain, the controller's owningWindow() only
            // exposed the dialog itself, so the registry provides
            // the real main stage
            MainWindowRegistry.mainWindow().ifPresent(stage::initOwner);

            stage.setScene(scene);

            StageHandle handle = new StageHandle(stage);

            // Attached through the handle on purpose: both handlers
            // are addEventHandler-based and must survive the manager
            // registration without clobbering each other.
            handle.addWindowHiddenHandler(controller::dispose);

            VideoDialogManager.register(call.getId(), handle);

            stage.show();

        } catch (Exception exception) {

            throw new RuntimeException("Failed to open video call dialog", exception);
        }
    }
}
