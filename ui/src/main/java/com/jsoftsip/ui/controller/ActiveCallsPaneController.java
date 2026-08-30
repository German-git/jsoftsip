package com.jsoftsip.ui.controller;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallListener;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.cell.CallListCell;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import javafx.scene.control.Label;

import com.jsoftsip.ui.I18n;

public class ActiveCallsPaneController {

    @FXML
    private ListView<CallLeg> activeCallsListView;

    @FXML
    private Label titleLabel;

    private final CallService callService;

    private final AppContext context;

    private final CallListener callListener = call -> Platform.runLater(this::refreshCalls);

    public ActiveCallsPaneController(AppContext context) {

        this.context = context;

        this.callService = context.getCallService();
    }

    @FXML
    private void initialize() {

        activeCallsListView.setCellFactory(list -> new CallListCell(context));

        callService.addListener(callListener);

        refreshCalls();

        I18n.bind(titleLabel.textProperty(), "active.calls.title");
    }

    private void refreshCalls() {

        activeCallsListView.getItems().setAll(callService.getActiveCalls());
    }
}