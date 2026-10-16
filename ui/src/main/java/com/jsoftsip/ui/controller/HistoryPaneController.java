package com.jsoftsip.ui.controller;

import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.util.CallDirectionPresentation;
import com.jsoftsip.core.util.CallDurationFormatter;
import com.jsoftsip.ui.util.CallResultPresentation;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class HistoryPaneController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private ListView<CallHistoryEntry> historyListView;

    @FXML
    private TitledPane historyTitle;

    private final HistoryService historyService;

    private final ExecutorService uiExecutor;

    public HistoryPaneController(AppContext context) {

        this.historyService = context.getHistoryService();

        this.uiExecutor = context.getUiExecutor();
    }

    @FXML
    private void initialize() {

        // refreshHistory self-offloads to the ui executor, so the
        // listener no longer needs a runLater wrapper regardless of
        // which thread fires it
        historyService.addListener(this::refreshHistory);

        I18n.bind(historyTitle.textProperty(), "history.title");

        historyListView.setCellFactory(listView -> new ListCell<CallHistoryEntry>() {

            private final ContextMenu contextMenu;

            {

                contextMenu = new ContextMenu(
                    createCopyMenuItem("history.copy.row", this, HistoryPaneController::formatRowData),
                    createCopyMenuItem("history.copy.destination", this, HistoryPaneController::destinationForCopy),
                    createCopyMenuItem("history.copy.origin", this, HistoryPaneController::originForCopy));
            }

            @Override
            protected void updateItem(CallHistoryEntry entry, boolean empty) {

                super.updateItem(entry, empty);

                if (empty || entry == null) {

                    setGraphic(null);

                    setText(null);

                    setContextMenu(null);

                    return;
                }

                setText(null);

                setGraphic(createRow(entry));

                setContextMenu(contextMenu);
            }
        });

        refreshHistory();
    }

    public void refreshHistory() {

        // The SQLite read stays off the FX thread:
        // the fetched entries publish through runLater
        uiExecutor.execute(() -> {

            List<CallHistoryEntry> entries = historyService.getHistory();

            Platform.runLater(() -> historyListView.getItems().setAll(entries));
        });
    }

    private VBox createRow(CallHistoryEntry entry) {

        Label timeLabel = new Label(formatTime(entry.getStartedAt()));

        timeLabel.getStyleClass().add("history-time");

        Label accountLabel = new Label(entry.getAccountUsername());

        accountLabel.getStyleClass().add("history-account");

        CallDirectionPresentation presentation = CallDirectionPresentation.forDirection(entry.getDirection());

        Text directionIcon = new Text(presentation.glyph());

        directionIcon.getStyleClass().addAll("history-direction", presentation.cssClass());

        Label destinationLabel = new Label(entry.getDestination());

        destinationLabel.getStyleClass().add("history-destination");

        HBox lineOne = new HBox(6, accountLabel, directionIcon, destinationLabel);

        lineOne.setAlignment(Pos.CENTER_LEFT);

        CallResultPresentation result = CallResultPresentation.forResult(entry.getResult());

        Label durationLabel = new Label(CallDurationFormatter.format(entry.getDurationSeconds()));

        durationLabel.getStyleClass().add("history-duration");

        Label resultLabel = new Label(result.displayText());

        resultLabel.getStyleClass().addAll("history-result", result.cssClass());

        HBox lineTwo = new HBox(6, durationLabel, resultLabel);

        lineTwo.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(2, timeLabel, lineOne, lineTwo);

        row.getStyleClass().add("history-row");

        return row;
    }

    private MenuItem createCopyMenuItem(String key, ListCell<CallHistoryEntry> cell,
                                        Function<CallHistoryEntry, String> textExtractor) {

        MenuItem menuItem = new MenuItem();

        menuItem.textProperty().bind(I18n.createStringBinding(key));

        menuItem.setOnAction(event -> {

            CallHistoryEntry entry = cell.getItem();

            if (entry != null) {
                copyToClipboard(textExtractor.apply(entry));
            }
        });

        return menuItem;
    }

    private static void copyToClipboard(String text) {

        ClipboardContent content = new ClipboardContent();

        content.putString(text);

        Clipboard.getSystemClipboard().setContent(content);
    }

    static String formatRowData(CallHistoryEntry entry) {

        CallDirectionPresentation direction = CallDirectionPresentation.forDirection(entry.getDirection());

        CallResultPresentation result = CallResultPresentation.forResult(entry.getResult());

        String participantLine = displayText(entry.getAccountUsername()) + " " + direction.glyph() + " "
            + displayText(entry.getDestination());

        String resultLine = CallDurationFormatter.format(entry.getDurationSeconds()) + " " + result.displayText();

        return String.join(System.lineSeparator(), formatTime(entry.getStartedAt()), participantLine, resultLine);
    }

    static String destinationForCopy(CallHistoryEntry entry) {

        return entry.getDirection() == CallDirection.INCOMING
            ? displayText(entry.getAccountUsername())
            : displayText(entry.getDestination());
    }

    static String originForCopy(CallHistoryEntry entry) {

        return entry.getDirection() == CallDirection.INCOMING
            ? displayText(entry.getDestination())
            : displayText(entry.getAccountUsername());
    }

    private static String displayText(String value) {

        return value != null ? value : "";
    }

    private static String formatTime(LocalDateTime startedAt) {

        if (startedAt == null) {

            return "";
        }

        return TIME_FORMATTER.format(startedAt);
    }
}
