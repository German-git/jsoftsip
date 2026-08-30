package com.jsoftsip.ui.cell;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.ControllerFactory;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.controller.CallCardController;
import com.jsoftsip.ui.dialog.VideoCallDialogFactory;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

public class CallListCell extends ListCell<CallLeg> {

    private final AppContext context;

    private CallCardController controller;

    private Region graphicRoot;

    public CallListCell(AppContext context) {
        this.context = context;
    }

    @Override
    protected void updateItem(CallLeg call, boolean empty) {

        super.updateItem(call, empty);

        if (empty || call == null) {

            disposeController();

            setGraphic(null);

            setText(null);

            return;
        }

        if (controller == null) {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CallCard.fxml"));

            loader.setResources(I18n.bundle());
            loader.setControllerFactory(new ControllerFactory(context));

            try {

                graphicRoot = loader.load();

            } catch (Exception exception) {

                throw new RuntimeException(exception);
            }

            controller = loader.getController();

            controller.setVideoOpener(c -> VideoCallDialogFactory.open(context, c));

            setText(null);

            setGraphic(graphicRoot);
        }

        controller.setCall(call);
    }

    private void disposeController() {

        if (controller != null) {

            controller.dispose();

            controller = null;

            graphicRoot = null;
        }
    }
}
