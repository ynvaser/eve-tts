package systems.bdev.evetts.ui.view;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.TtsSpeechService;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View component for configuring, testing, and persisting String & Pronunciation Replacements.
 */
public class ReplacementsTabView extends ScrollPane {

    private static final Logger logger = LoggerFactory.getLogger(ReplacementsTabView.class);

    private final ConfigurationService configurationService;
    private final VoiceCatalogService voiceCatalogService;
    private final AppService appService;

    private final ObservableList<ReplacementEntry> replacementList = FXCollections.observableArrayList();
    private final TableView<ReplacementEntry> replacementTable = new TableView<>();

    // Inputs
    private final TextField targetField = new TextField();
    private final TextField replacementField = new TextField();

    // Live Test & Audition
    private final TextField testInputField = new TextField("FC align to Jita stargate, check dscan.");
    private final Label testOutputPreviewLabel = new Label();
    private final ComboBox<String> auditionEngineComboBox = new ComboBox<>();
    private final ComboBox<String> auditionVoiceComboBox = new ComboBox<>();
    private final Button testAuditionButton = new Button("Audition Pronunciation");
    private final Label auditionStatusLabel = new Label();

    // Feedback
    private final Label statusMessageLabel = new Label();

    public ReplacementsTabView(
            ConfigurationService configurationService,
            VoiceCatalogService voiceCatalogService,
            AppService appService
    ) {
        this.configurationService = configurationService;
        this.voiceCatalogService = voiceCatalogService;
        this.appService = appService;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent;");

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(12, 16, 12, 16));

        contentBox.getChildren().addAll(
                createReplacementsTableCard(),
                createLiveTestCard(),
                createActionButtonsCard()
        );

        setContent(contentBox);
        loadReplacementsFromConfiguration();
        setupLivePreviewListener();
    }

    private VBox createReplacementsTableCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("String & Pronunciation Replacements");
        cardTitle.getStyleClass().add("card-title");

        Label cardSubtitle = new Label("Define custom substitutions to fix model pronunciations, expand acronyms, or replace abbreviations. Replacements are applied case-insensitively before voice synthesis.");
        cardSubtitle.getStyleClass().add("card-subtitle");
        cardSubtitle.setWrapText(true);

        replacementTable.setItems(replacementList);
        replacementTable.setPrefHeight(220);
        replacementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        replacementTable.setEditable(true);

        TableColumn<ReplacementEntry, String> targetCol = new TableColumn<>("Target Substring (Matches Case-Insensitively)");
        targetCol.setCellValueFactory(new PropertyValueFactory<>("target"));
        targetCol.setCellFactory(TextFieldTableCell.forTableColumn());
        targetCol.setOnEditCommit(e -> {
            e.getRowValue().setTarget(e.getNewValue());
            updateLivePreview();
        });

        TableColumn<ReplacementEntry, String> replacementCol = new TableColumn<>("Pronunciation / Replacement Text");
        replacementCol.setCellValueFactory(new PropertyValueFactory<>("replacement"));
        replacementCol.setCellFactory(TextFieldTableCell.forTableColumn());
        replacementCol.setOnEditCommit(e -> {
            e.getRowValue().setReplacement(e.getNewValue());
            updateLivePreview();
        });

        replacementTable.getColumns().addAll(targetCol, replacementCol);
        replacementTable.setPlaceholder(new Label("No string replacements configured. Add a replacement below."));

        // Controls row
        targetField.setPromptText("Target string (e.g. dscan, FC, jita)...");
        targetField.setPrefWidth(240);

        replacementField.setPromptText("Replacement text (e.g. D-scan, Fleet Commander, Jee-tah)...");
        HBox.setHgrow(replacementField, Priority.ALWAYS);

        Button addBtn = new Button("Add / Update Rule");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> handleAddOrUpdate());

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setOnAction(e -> {
            ReplacementEntry selected = replacementTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                replacementList.remove(selected);
                updateLivePreview();
            }
        });

        Button clearBtn = new Button("Clear All");
        clearBtn.setOnAction(e -> {
            if (!replacementList.isEmpty()) {
                replacementList.clear();
                updateLivePreview();
            }
        });

        HBox controlsRow = new HBox(8, targetField, replacementField, addBtn, removeBtn, clearBtn);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardTitle, cardSubtitle, replacementTable, controlsRow);
        return card;
    }

    private VBox createLiveTestCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("Live Test & Pronunciation Audition");
        cardTitle.getStyleClass().add("card-title");

        Label cardSubtitle = new Label("Test your configured replacement rules in real-time and audition the voice output.");
        cardSubtitle.getStyleClass().add("card-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label inputLabel = new Label("Test Input Text:");
        inputLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: 500;");
        HBox.setHgrow(testInputField, Priority.ALWAYS);

        Label outputHeader = new Label("Synthesizer Will Say:");
        outputHeader.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: 500;");

        testOutputPreviewLabel.setStyle("-fx-text-fill: #4dd0e1; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-color: #1e2227; -fx-padding: 6 10; -fx-background-radius: 4;");
        testOutputPreviewLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(testOutputPreviewLabel, Priority.ALWAYS);

        grid.add(inputLabel, 0, 0);
        grid.add(testInputField, 1, 0);
        grid.add(outputHeader, 0, 1);
        grid.add(testOutputPreviewLabel, 1, 1);

        // Audition controls
        auditionEngineComboBox.getItems().addAll(voiceCatalogService.getSupportedEngines());
        auditionEngineComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            auditionVoiceComboBox.setItems(FXCollections.observableArrayList(voiceCatalogService.getAvailableVoices(newVal)));
            auditionVoiceComboBox.getSelectionModel().select(voiceCatalogService.getDefaultVoice(newVal));
        });

        testAuditionButton.getStyleClass().add("btn-primary");
        testAuditionButton.setOnAction(e -> performAudition());

        auditionStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #00bcd4;");

        HBox auditionRow = new HBox(8,
                new Label("Engine:"), auditionEngineComboBox,
                new Label("Voice:"), auditionVoiceComboBox,
                testAuditionButton,
                auditionStatusLabel
        );
        auditionRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardTitle, cardSubtitle, grid, auditionRow);
        return card;
    }

    private VBox createActionButtonsCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Save Replacements");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> saveReplacements());

        Button reloadBtn = new Button("Reload from Disk");
        reloadBtn.setOnAction(e -> {
            try {
                configurationService.reloadConfiguration();
                loadReplacementsFromConfiguration();
                showStatus("Replacements reloaded from disk.", false);
            } catch (Exception ex) {
                showStatus("Failed to reload: " + ex.getMessage(), true);
            }
        });

        statusMessageLabel.setStyle("-fx-font-weight: bold;");

        actionRow.getChildren().addAll(saveBtn, reloadBtn, statusMessageLabel);
        card.getChildren().add(actionRow);
        return card;
    }

    private void setupLivePreviewListener() {
        testInputField.textProperty().addListener((obs, oldVal, newVal) -> updateLivePreview());
        replacementList.addListener((ListChangeListener<ReplacementEntry>) c -> updateLivePreview());
        updateLivePreview();
    }

    private void updateLivePreview() {
        String input = testInputField.getText();
        if (input == null || input.isEmpty()) {
            testOutputPreviewLabel.setText("");
            return;
        }

        Map<String, String> replacements = getCurrentReplacementsMap();
        String result = TtsSpeechService.applyReplacements(input, replacements);
        testOutputPreviewLabel.setText(result);
    }

    private Map<String, String> getCurrentReplacementsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ReplacementEntry entry : replacementList) {
            if (entry.getTarget() != null && !entry.getTarget().trim().isEmpty() &&
                    entry.getReplacement() != null) {
                map.put(entry.getTarget().trim(), entry.getReplacement().trim());
            }
        }
        return map;
    }

    private void handleAddOrUpdate() {
        String target = targetField.getText();
        String replacement = replacementField.getText();

        if (target == null || target.trim().isEmpty()) {
            showStatus("Error: Target substring cannot be empty.", true);
            return;
        }

        String cleanTarget = target.trim();
        String cleanReplacement = (replacement != null) ? replacement.trim() : "";

        // Check if existing rule exists for this target (case-insensitive)
        ReplacementEntry existing = null;
        for (ReplacementEntry entry : replacementList) {
            if (entry.getTarget() != null && entry.getTarget().equalsIgnoreCase(cleanTarget)) {
                existing = entry;
                break;
            }
        }

        if (existing != null) {
            existing.setReplacement(cleanReplacement);
            showStatus("Updated replacement for '" + cleanTarget + "'.", false);
        } else {
            replacementList.add(new ReplacementEntry(cleanTarget, cleanReplacement));
            showStatus("Added replacement for '" + cleanTarget + "'.", false);
        }

        targetField.clear();
        replacementField.clear();
        updateLivePreview();
    }

    public void loadReplacementsFromConfiguration() {
        AppConfiguration config = configurationService.getConfiguration();
        if (config == null) return;

        replacementList.clear();
        Map<String, String> replacements = config.getStringReplacements();
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                replacementList.add(new ReplacementEntry(entry.getKey(), entry.getValue()));
            }
        }

        auditionEngineComboBox.setValue(config.getTtsEngine());
        auditionVoiceComboBox.setItems(FXCollections.observableArrayList(voiceCatalogService.getAvailableVoices(config.getTtsEngine())));
        auditionVoiceComboBox.setValue(voiceCatalogService.getDefaultVoice(config.getTtsEngine()));

        updateLivePreview();
    }

    private void saveReplacements() {
        try {
            AppConfiguration config = configurationService.getConfiguration();
            if (config == null) {
                config = new AppConfiguration();
            }

            Map<String, String> map = getCurrentReplacementsMap();
            config.setStringReplacements(map);

            configurationService.updateConfiguration(config);
            configurationService.saveConfiguration();

            showStatus("String replacements successfully saved to config.json.", false);
        } catch (Exception e) {
            logger.error("Failed to save string replacements: {}", e.getMessage(), e);
            showStatus("Failed to save: " + e.getMessage(), true);
        }
    }

    private void performAudition() {
        String engine = auditionEngineComboBox.getValue();
        String voice = auditionVoiceComboBox.getValue();
        String text = testOutputPreviewLabel.getText();

        if (engine == null || voice == null) {
            auditionStatusLabel.setText("Select an engine and voice.");
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            auditionStatusLabel.setText("Please enter test text to audition.");
            return;
        }

        AppConfiguration config = configurationService.getConfiguration();
        String audioDevice = config != null ? config.getAudioDevice() : null;

        testAuditionButton.setDisable(true);
        testAuditionButton.setText("Speaking...");
        auditionStatusLabel.setText("Synthesizing speech via " + engine + "...");

        appService.testVoice(engine, voice, audioDevice, text)
                .thenRun(() -> Platform.runLater(() -> {
                    testAuditionButton.setDisable(false);
                    testAuditionButton.setText("Audition Pronunciation");
                    auditionStatusLabel.setText("Audition completed successfully.");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        testAuditionButton.setDisable(false);
                        testAuditionButton.setText("Audition Pronunciation");
                        auditionStatusLabel.setText("Audition error: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void showStatus(String message, boolean isError) {
        statusMessageLabel.setText(message);
        statusMessageLabel.setStyle(isError ? "-fx-text-fill: #e06c75;" : "-fx-text-fill: #49aa52;");
    }

    public static class ReplacementEntry {
        private final StringProperty target = new SimpleStringProperty();
        private final StringProperty replacement = new SimpleStringProperty();

        public ReplacementEntry(String target, String replacement) {
            setTarget(target);
            setReplacement(replacement);
        }

        public String getTarget() {
            return target.get();
        }

        public void setTarget(String value) {
            this.target.set(value);
        }

        public StringProperty targetProperty() {
            return target;
        }

        public String getReplacement() {
            return replacement.get();
        }

        public void setReplacement(String value) {
            this.replacement.set(value);
        }

        public StringProperty replacementProperty() {
            return replacement;
        }
    }
}
