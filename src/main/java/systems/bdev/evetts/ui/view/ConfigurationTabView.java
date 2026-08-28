package systems.bdev.evetts.ui.view;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * View component for managing Application Configuration, Channel Listeners,
 * Character Voice Mappings, and Live Voice Auditioning.
 */
public class ConfigurationTabView extends ScrollPane {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationTabView.class);

    private final ConfigurationService configurationService;
    private final AudioDeviceService audioDeviceService;
    private final VoiceCatalogService voiceCatalogService;
    private final AppService appService;

    // General fields
    private final TextField logDirectoryField = new TextField();
    private final TextField pollIntervalField = new TextField();
    private final ComboBox<String> engineComboBox = new ComboBox<>();
    private final ComboBox<String> audioDeviceComboBox = new ComboBox<>();
    private final CheckBox enableTtsCheckBox = new CheckBox("Enable Text-to-Speech (TTS)");
    private final CheckBox useGpuCheckBox = new CheckBox("Enable GPU (CUDA) Acceleration");

    // Channels
    private final ObservableList<String> channelList = FXCollections.observableArrayList();
    private final TableView<String> channelsTable = new TableView<>();
    private final TextField newChannelField = new TextField();

    // Voice Mappings
    private final ObservableList<VoiceMappingEntry> voiceMappingList = FXCollections.observableArrayList();
    private final TableView<VoiceMappingEntry> voiceMappingTable = new TableView<>();
    private final TextField newPilotField = new TextField();
    private final ComboBox<String> newVoiceComboBox = new ComboBox<>();

    // Audition Section
    private final ComboBox<String> auditionEngineComboBox = new ComboBox<>();
    private final ComboBox<String> auditionVoiceComboBox = new ComboBox<>();
    private final ComboBox<String> auditionDeviceComboBox = new ComboBox<>();
    private final TextField auditionTextField = new TextField();
    private final Button auditionButton = new Button("Audition Voice");
    private final Label auditionStatusLabel = new Label();

    // Feedback
    private final Label statusMessageLabel = new Label();

    public ConfigurationTabView(
            ConfigurationService configurationService,
            AudioDeviceService audioDeviceService,
            VoiceCatalogService voiceCatalogService,
            AppService appService
    ) {
        this.configurationService = configurationService;
        this.audioDeviceService = audioDeviceService;
        this.voiceCatalogService = voiceCatalogService;
        this.appService = appService;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent;");

        VBox contentBox = new VBox(16);
        contentBox.setPadding(new Insets(16));

        contentBox.getChildren().addAll(
                createGeneralSettingsCard(),
                createChannelsAndVoicesCard(),
                createAuditionCard(),
                createActionButtonsCard()
        );

        setContent(contentBox);
        loadConfigurationValues();
    }

    private VBox createGeneralSettingsCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("General & Hardware Settings");
        cardTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        // EVE Log Directory
        Label logDirLabel = new Label("EVE Chatlogs Directory:");
        logDirLabel.setStyle("-fx-text-fill: #8b949e;");
        logDirectoryField.setPromptText("Path to Documents/EVE/logs/Chatlogs");
        HBox.setHgrow(logDirectoryField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select EVE Chatlogs Directory");
            String current = logDirectoryField.getText();
            if (current != null && !current.trim().isEmpty()) {
                File dir = new File(current.trim());
                if (dir.exists() && dir.isDirectory()) {
                    chooser.setInitialDirectory(dir);
                }
            }
            File selected = chooser.showDialog(getScene().getWindow());
            if (selected != null) {
                logDirectoryField.setText(selected.getAbsolutePath());
            }
        });

        HBox logDirBox = new HBox(8, logDirectoryField, browseBtn);
        logDirBox.setAlignment(Pos.CENTER_LEFT);

        // Poll Interval
        Label pollLabel = new Label("Polling Interval (ms):");
        pollLabel.setStyle("-fx-text-fill: #8b949e;");
        pollIntervalField.setPromptText("500");
        pollIntervalField.setPrefWidth(120);

        // Engine
        Label engineLabel = new Label("TTS Engine:");
        engineLabel.setStyle("-fx-text-fill: #8b949e;");
        engineComboBox.getItems().addAll(voiceCatalogService.getSupportedEngines());
        engineComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            refreshAvailableVoices(newVal);
        });

        // Audio Device
        Label audioLabel = new Label("Audio Output Device:");
        audioLabel.setStyle("-fx-text-fill: #8b949e;");
        audioDeviceComboBox.setPrefWidth(280);
        Button refreshAudioBtn = new Button("Refresh Devices");
        refreshAudioBtn.setOnAction(e -> refreshAudioDeviceList());

        HBox audioBox = new HBox(8, audioDeviceComboBox, refreshAudioBtn);
        audioBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(logDirLabel, 0, 0);
        grid.add(logDirBox, 1, 0);
        grid.add(pollLabel, 0, 1);
        grid.add(pollIntervalField, 1, 1);
        grid.add(engineLabel, 0, 2);
        grid.add(engineComboBox, 1, 2);
        grid.add(audioLabel, 0, 3);
        grid.add(audioBox, 1, 3);

        HBox togglesBox = new HBox(20, enableTtsCheckBox, useGpuCheckBox);
        togglesBox.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardTitle, grid, togglesBox);
        return card;
    }

    private HBox createChannelsAndVoicesCard() {
        HBox container = new HBox(16);

        // Left Card: Channels
        VBox channelsCard = new VBox(10);
        channelsCard.getStyleClass().add("card");
        HBox.setHgrow(channelsCard, Priority.ALWAYS);

        Label chanTitle = new Label("Monitored Chat Channels");
        chanTitle.getStyleClass().add("card-title");

        channelsTable.setItems(channelList);
        channelsTable.setPrefHeight(180);
        channelsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String, String> nameCol = new TableColumn<>("Channel Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        channelsTable.getColumns().add(nameCol);

        newChannelField.setPromptText("Enter channel name...");
        Button addChanBtn = new Button("Add Channel");
        addChanBtn.setOnAction(e -> {
            String name = newChannelField.getText();
            if (name != null && !name.trim().isEmpty() && !channelList.contains(name.trim())) {
                channelList.add(name.trim());
                newChannelField.clear();
            }
        });

        Button removeChanBtn = new Button("Remove Selected");
        removeChanBtn.setOnAction(e -> {
            String selected = channelsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                channelList.remove(selected);
            }
        });

        HBox chanControls = new HBox(8, newChannelField, addChanBtn, removeChanBtn);
        chanControls.setAlignment(Pos.CENTER_LEFT);

        channelsCard.getChildren().addAll(chanTitle, channelsTable, chanControls);

        // Right Card: Character Voices
        VBox voicesCard = new VBox(10);
        voicesCard.getStyleClass().add("card");
        HBox.setHgrow(voicesCard, Priority.ALWAYS);

        Label voicesTitle = new Label("Character Voice Mappings");
        voicesTitle.getStyleClass().add("card-title");

        voiceMappingTable.setItems(voiceMappingList);
        voiceMappingTable.setPrefHeight(180);
        voiceMappingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        voiceMappingTable.setEditable(true);

        TableColumn<VoiceMappingEntry, String> pilotCol = new TableColumn<>("Pilot Name");
        pilotCol.setCellValueFactory(new PropertyValueFactory<>("pilotName"));
        pilotCol.setCellFactory(TextFieldTableCell.forTableColumn());
        pilotCol.setOnEditCommit(e -> e.getRowValue().setPilotName(e.getNewValue()));

        TableColumn<VoiceMappingEntry, String> voiceCol = new TableColumn<>("Voice Model");
        voiceCol.setCellValueFactory(new PropertyValueFactory<>("voiceModel"));
        voiceMappingTable.getColumns().addAll(pilotCol, voiceCol);

        newPilotField.setPromptText("Pilot Name...");
        newVoiceComboBox.setPromptText("Select Voice");
        newVoiceComboBox.setPrefWidth(140);

        Button addVoiceBtn = new Button("Add Mapping");
        addVoiceBtn.setOnAction(e -> {
            String pilot = newPilotField.getText();
            String voice = newVoiceComboBox.getValue();
            if (pilot != null && !pilot.trim().isEmpty() && voice != null && !voice.trim().isEmpty()) {
                voiceMappingList.add(new VoiceMappingEntry(pilot.trim(), voice.trim()));
                newPilotField.clear();
            }
        });

        Button removeVoiceBtn = new Button("Remove Selected");
        removeVoiceBtn.setOnAction(e -> {
            VoiceMappingEntry selected = voiceMappingTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                voiceMappingList.remove(selected);
            }
        });

        HBox voiceControls = new HBox(8, newPilotField, newVoiceComboBox, addVoiceBtn, removeVoiceBtn);
        voiceControls.setAlignment(Pos.CENTER_LEFT);

        voicesCard.getChildren().addAll(voicesTitle, voiceMappingTable, voiceControls);

        container.getChildren().addAll(channelsCard, voicesCard);
        return container;
    }

    private VBox createAuditionCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("Voice Audition & Testing");
        cardTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        auditionEngineComboBox.getItems().addAll(voiceCatalogService.getSupportedEngines());
        auditionEngineComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            auditionVoiceComboBox.setItems(FXCollections.observableArrayList(voiceCatalogService.getAvailableVoices(newVal)));
            auditionVoiceComboBox.getSelectionModel().select(voiceCatalogService.getDefaultVoice(newVal));
        });

        auditionTextField.setText("Fleet warp drive active. All pilots align to stargate.");
        HBox.setHgrow(auditionTextField, Priority.ALWAYS);

        auditionButton.getStyleClass().add("btn-primary");
        auditionButton.setOnAction(e -> performVoiceAudition());

        auditionStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #00bcd4;");

        HBox auditionRow = new HBox(8,
                new Label("Engine:"), auditionEngineComboBox,
                new Label("Voice:"), auditionVoiceComboBox,
                new Label("Device:"), auditionDeviceComboBox,
                auditionTextField, auditionButton);
        auditionRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardTitle, auditionRow, auditionStatusLabel);
        return card;
    }

    private VBox createActionButtonsCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Save Configuration");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> saveConfiguration());

        Button reloadBtn = new Button("Reload from Disk");
        reloadBtn.setOnAction(e -> {
            try {
                configurationService.reloadConfiguration();
                loadConfigurationValues();
                showStatus("Configuration reloaded from disk.", false);
            } catch (Exception ex) {
                showStatus("Failed to reload: " + ex.getMessage(), true);
            }
        });

        Button resetBtn = new Button("Reset to Defaults");
        resetBtn.setOnAction(e -> {
            AppConfiguration defaults = configurationService.createDefaultConfiguration();
            configurationService.updateConfiguration(defaults);
            loadConfigurationValues();
            showStatus("Reset to default configuration. Click Save to persist.", false);
        });

        statusMessageLabel.setStyle("-fx-font-weight: bold;");

        actionRow.getChildren().addAll(saveBtn, reloadBtn, resetBtn, statusMessageLabel);
        card.getChildren().add(actionRow);
        return card;
    }

    private void performVoiceAudition() {
        String engine = auditionEngineComboBox.getValue();
        String voice = auditionVoiceComboBox.getValue();
        String device = auditionDeviceComboBox.getValue();
        String text = auditionTextField.getText();

        if (engine == null || voice == null) {
            auditionStatusLabel.setText("Please select an engine and voice model.");
            return;
        }

        auditionButton.setDisable(true);
        auditionButton.setText("Speaking...");
        auditionStatusLabel.setText("Synthesizing speech via " + engine + "...");

        appService.testVoice(engine, voice, device, text)
                .thenRun(() -> Platform.runLater(() -> {
                    auditionButton.setDisable(false);
                    auditionButton.setText("Audition Voice");
                    auditionStatusLabel.setText("Audition completed successfully.");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        auditionButton.setDisable(false);
                        auditionButton.setText("Audition Voice");
                        auditionStatusLabel.setText("Audition error: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void refreshAudioDeviceList() {
        List<String> devices = audioDeviceService.getAvailableAudioDevices();
        String currentAudio = audioDeviceComboBox.getValue();
        String currentAudition = auditionDeviceComboBox.getValue();

        audioDeviceComboBox.setItems(FXCollections.observableArrayList(devices));
        auditionDeviceComboBox.setItems(FXCollections.observableArrayList(devices));

        if (currentAudio != null && devices.contains(currentAudio)) {
            audioDeviceComboBox.setValue(currentAudio);
        } else if (!devices.isEmpty()) {
            audioDeviceComboBox.getSelectionModel().selectFirst();
        }

        if (currentAudition != null && devices.contains(currentAudition)) {
            auditionDeviceComboBox.setValue(currentAudition);
        } else if (!devices.isEmpty()) {
            auditionDeviceComboBox.getSelectionModel().selectFirst();
        }
    }

    private void refreshAvailableVoices(String engine) {
        List<String> voices = voiceCatalogService.getAvailableVoices(engine);
        newVoiceComboBox.setItems(FXCollections.observableArrayList(voices));
        if (!voices.isEmpty()) {
            newVoiceComboBox.getSelectionModel().select(voiceCatalogService.getDefaultVoice(engine));
        }
    }

    public void loadConfigurationValues() {
        AppConfiguration config = configurationService.getConfiguration();
        if (config == null) return;

        logDirectoryField.setText(config.getEveLogDirectory());
        pollIntervalField.setText(String.valueOf(config.getPollIntervalMs()));
        engineComboBox.setValue(config.getTtsEngine());
        enableTtsCheckBox.setSelected(config.isEnableTts());
        useGpuCheckBox.setSelected(config.isUseGpu());

        refreshAudioDeviceList();
        if (config.getAudioDevice() != null) {
            audioDeviceComboBox.setValue(config.getAudioDevice());
            auditionDeviceComboBox.setValue(config.getAudioDevice());
        }

        auditionEngineComboBox.setValue(config.getTtsEngine());
        refreshAvailableVoices(config.getTtsEngine());

        channelList.setAll(config.getChannels() != null ? config.getChannels() : List.of());

        voiceMappingList.clear();
        if (config.getCharacterVoices() != null) {
            for (Map.Entry<String, String> entry : config.getCharacterVoices().entrySet()) {
                voiceMappingList.add(new VoiceMappingEntry(entry.getKey(), entry.getValue()));
            }
        }
    }

    private void saveConfiguration() {
        try {
            AppConfiguration config = configurationService.getConfiguration();
            if (config == null) {
                config = new AppConfiguration();
            }

            config.setEveLogDirectory(logDirectoryField.getText().trim());

            try {
                long poll = Long.parseLong(pollIntervalField.getText().trim());
                config.setPollIntervalMs(Math.max(100L, poll));
            } catch (NumberFormatException e) {
                config.setPollIntervalMs(500L);
            }

            config.setTtsEngine(engineComboBox.getValue());
            config.setAudioDevice(audioDeviceComboBox.getValue());
            config.setEnableTts(enableTtsCheckBox.isSelected());
            config.setUseGpu(useGpuCheckBox.isSelected());

            config.setChannels(new ArrayList<>(channelList));

            Map<String, String> voices = new HashMap<>();
            for (VoiceMappingEntry entry : voiceMappingList) {
                if (entry.getPilotName() != null && !entry.getPilotName().trim().isEmpty() &&
                        entry.getVoiceModel() != null && !entry.getVoiceModel().trim().isEmpty()) {
                    voices.put(entry.getPilotName().trim(), entry.getVoiceModel().trim());
                }
            }

            if (voices.isEmpty()) {
                showStatus("Error: At least one Character Voice Mapping is required!", true);
                return;
            }

            config.setCharacterVoices(voices);
            configurationService.updateConfiguration(config);
            configurationService.saveConfiguration();

            showStatus("Configuration saved successfully. If service is running, restart to apply new engine/channels.", false);
        } catch (Exception e) {
            logger.error("Failed to save configuration: {}", e.getMessage(), e);
            showStatus("Failed to save: " + e.getMessage(), true);
        }
    }

    private void showStatus(String message, boolean isError) {
        statusMessageLabel.setText(message);
        statusMessageLabel.setStyle(isError ? "-fx-text-fill: #e06c75;" : "-fx-text-fill: #49aa52;");
    }

    public static class VoiceMappingEntry {
        private final StringProperty pilotName = new SimpleStringProperty();
        private final StringProperty voiceModel = new SimpleStringProperty();

        public VoiceMappingEntry(String pilot, String voice) {
            setPilotName(pilot);
            setVoiceModel(voice);
        }

        public String getPilotName() {
            return pilotName.get();
        }

        public void setPilotName(String value) {
            this.pilotName.set(value);
        }

        public StringProperty pilotNameProperty() {
            return pilotName;
        }

        public String getVoiceModel() {
            return voiceModel.get();
        }

        public void setVoiceModel(String value) {
            this.voiceModel.set(value);
        }

        public StringProperty voiceModelProperty() {
            return voiceModel;
        }
    }
}
