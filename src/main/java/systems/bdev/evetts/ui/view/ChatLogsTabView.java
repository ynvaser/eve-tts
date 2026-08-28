package systems.bdev.evetts.ui.view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import systems.bdev.evetts.model.ChatEvent;
import systems.bdev.evetts.model.ChatEventStatus;

import java.util.HashSet;
import java.util.Set;

/**
 * View component for the Live Chat Logs tab.
 * Contains search/filter controls and a bounded (FIFO max 1,000 entries) TableView.
 */
public class ChatLogsTabView extends VBox {

    private static final int MAX_LOG_ENTRIES = 1000;

    private final ObservableList<ChatEvent> masterEvents = FXCollections.observableArrayList();
    private final FilteredList<ChatEvent> filteredEvents;
    private final TableView<ChatEvent> tableView = new TableView<>();

    private final ComboBox<String> channelFilter = new ComboBox<>();
    private final TextField pilotSearchField = new TextField();
    private final TextField textSearchField = new TextField();
    private final CheckBox onlySpokenCheckbox = new CheckBox("Only Spoken");
    private final CheckBox autoScrollCheckbox = new CheckBox("Auto-Scroll");
    private final Label eventCountLabel = new Label("0 events");

    private final Set<String> knownChannels = new HashSet<>();

    public ChatLogsTabView() {
        setSpacing(12);
        setPadding(new Insets(14));
        VBox.setVgrow(this, Priority.ALWAYS);

        this.filteredEvents = new FilteredList<>(masterEvents, p -> true);

        channelFilter.getItems().add("All Channels");
        channelFilter.getSelectionModel().selectFirst();
        channelFilter.setPrefWidth(160);

        pilotSearchField.setPromptText("Search pilot...");
        pilotSearchField.setPrefWidth(160);

        textSearchField.setPromptText("Search message text...");
        HBox.setHgrow(textSearchField, Priority.ALWAYS);

        autoScrollCheckbox.setSelected(true);

        setupFilters();
        setupTableView();

        HBox filterBar = createFilterBar();
        getChildren().addAll(filterBar, tableView);
    }

    private HBox createFilterBar() {
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("card");
        filterBar.setPadding(new Insets(8, 12, 8, 12));

        Label filterLabel = new Label("Filter:");
        filterLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #8b949e;");

        Button clearBtn = new Button("Clear Log");
        clearBtn.setOnAction(e -> {
            masterEvents.clear();
            updateCountLabel();
        });

        eventCountLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        filterBar.getChildren().addAll(
                filterLabel,
                channelFilter,
                pilotSearchField,
                textSearchField,
                onlySpokenCheckbox,
                autoScrollCheckbox,
                clearBtn,
                eventCountLabel
        );

        return filterBar;
    }

    private void setupFilters() {
        channelFilter.valueProperty().addListener((obs, oldVal, newVal) -> updatePredicate());
        pilotSearchField.textProperty().addListener((obs, oldVal, newVal) -> updatePredicate());
        textSearchField.textProperty().addListener((obs, oldVal, newVal) -> updatePredicate());
        onlySpokenCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> updatePredicate());
    }

    private void updatePredicate() {
        String selectedChannel = channelFilter.getValue();
        String pilotFilter = pilotSearchField.getText() != null ? pilotSearchField.getText().trim().toLowerCase() : "";
        String textFilter = textSearchField.getText() != null ? textSearchField.getText().trim().toLowerCase() : "";
        boolean onlySpoken = onlySpokenCheckbox.isSelected();

        filteredEvents.setPredicate(event -> {
            if (event == null) return false;

            if (selectedChannel != null && !selectedChannel.equals("All Channels")) {
                if (!selectedChannel.equalsIgnoreCase(event.getChannelName())) {
                    return false;
                }
            }

            if (!pilotFilter.isEmpty()) {
                String sender = event.getSender();
                if (sender == null || !sender.toLowerCase().contains(pilotFilter)) {
                    return false;
                }
            }

            if (!textFilter.isEmpty()) {
                String msg = event.getMessage();
                if (msg == null || !msg.toLowerCase().contains(textFilter)) {
                    return false;
                }
            }

            if (onlySpoken && event.getStatus() != ChatEventStatus.SPOKEN) {
                return false;
            }

            return true;
        });

        updateCountLabel();
    }

    private void setupTableView() {
        SortedList<ChatEvent> sortedList = new SortedList<>(filteredEvents);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<ChatEvent, String> timeCol = new TableColumn<>("Time (UTC)");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setMinWidth(130);
        timeCol.setMaxWidth(160);

        TableColumn<ChatEvent, String> chanCol = new TableColumn<>("Channel");
        chanCol.setCellValueFactory(new PropertyValueFactory<>("channelName"));
        chanCol.setMinWidth(110);
        chanCol.setMaxWidth(160);

        TableColumn<ChatEvent, String> senderCol = new TableColumn<>("Pilot Name");
        senderCol.setCellValueFactory(new PropertyValueFactory<>("sender"));
        senderCol.setMinWidth(140);
        senderCol.setMaxWidth(200);

        TableColumn<ChatEvent, String> voiceCol = new TableColumn<>("Voice Model");
        voiceCol.setCellValueFactory(new PropertyValueFactory<>("voiceModel"));
        voiceCol.setMinWidth(110);
        voiceCol.setMaxWidth(160);

        TableColumn<ChatEvent, String> msgCol = new TableColumn<>("Message Text");
        msgCol.setCellValueFactory(new PropertyValueFactory<>("message"));
        msgCol.setMinWidth(250);

        TableColumn<ChatEvent, ChatEventStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setMinWidth(100);
        statusCol.setMaxWidth(120);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ChatEventStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label badge = new Label(status.name());
                    switch (status) {
                        case SPOKEN -> badge.getStyleClass().add("badge-spoken");
                        case FILTERED -> badge.getStyleClass().add("badge-filtered");
                        case ERROR -> badge.getStyleClass().add("badge-error");
                        case IGNORED -> badge.getStyleClass().add("badge-ignored");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        tableView.getColumns().addAll(timeCol, chanCol, senderCol, voiceCol, msgCol, statusCol);
        tableView.setPlaceholder(new Label("No chat messages recorded yet. Waiting for game log events..."));
    }

    public void addChatEvent(ChatEvent event) {
        if (event == null) return;

        Platform.runLater(() -> {
            // FIFO eviction when reaching bounded limit
            if (masterEvents.size() >= MAX_LOG_ENTRIES) {
                masterEvents.remove(0);
            }
            masterEvents.add(event);

            // Update channels dropdown if new channel observed
            String channel = event.getChannelName();
            if (channel != null && !channel.trim().isEmpty() && knownChannels.add(channel.trim())) {
                if (!channelFilter.getItems().contains(channel.trim())) {
                    channelFilter.getItems().add(channel.trim());
                }
            }

            updateCountLabel();

            if (autoScrollCheckbox.isSelected() && !filteredEvents.isEmpty()) {
                tableView.scrollTo(filteredEvents.size() - 1);
            }
        });
    }

    private void updateCountLabel() {
        int shown = filteredEvents.size();
        int total = masterEvents.size();
        eventCountLabel.setText(String.format("%d / %d events", shown, total));
    }
}
