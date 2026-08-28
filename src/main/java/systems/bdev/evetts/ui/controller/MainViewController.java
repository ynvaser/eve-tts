package systems.bdev.evetts.ui.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.model.ChatEvent;
import systems.bdev.evetts.model.ServiceState;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;
import systems.bdev.evetts.ui.view.ChatLogsTabView;
import systems.bdev.evetts.ui.view.ConfigurationTabView;
import systems.bdev.evetts.ui.view.DiagnosticsTabView;
import systems.bdev.evetts.ui.view.ReplacementsTabView;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Master UI controller connecting presentation views with backend domain service contracts.
 */
public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    private final AppService appService;
    private final ConfigurationService configurationService;
    private final AudioDeviceService audioDeviceService;
    private final VoiceCatalogService voiceCatalogService;

    private final BorderPane rootLayout = new BorderPane();
    private final TabPane tabPane = new TabPane();

    // Header Controls
    private final Label statusBadge = new Label("STOPPED");
    private final Label statusDetailLabel = new Label("Ready");
    private final Label channelCountBadge = new Label("Channels: 0");
    private final Button startButton = new Button("Start Service");
    private final Button stopButton = new Button("Stop Service");
    private final Button restartButton = new Button("Restart");

    // Views
    private ChatLogsTabView chatLogsView;
    private ConfigurationTabView configurationView;
    private ReplacementsTabView replacementsView;
    private DiagnosticsTabView diagnosticsView;

    public MainViewController(
            AppService appService,
            ConfigurationService configurationService,
            AudioDeviceService audioDeviceService,
            VoiceCatalogService voiceCatalogService
    ) {
        this.appService = Objects.requireNonNull(appService, "AppService cannot be null");
        this.configurationService = Objects.requireNonNull(configurationService, "ConfigurationService cannot be null");
        this.audioDeviceService = Objects.requireNonNull(audioDeviceService, "AudioDeviceService cannot be null");
        this.voiceCatalogService = Objects.requireNonNull(voiceCatalogService, "VoiceCatalogService cannot be null");

        initializeUI();
        registerServiceListeners();
        updateChannelCount();
    }

    private void initializeUI() {
        // Build Sub-Views
        this.chatLogsView = new ChatLogsTabView();
        this.configurationView = new ConfigurationTabView(
                configurationService,
                audioDeviceService,
                voiceCatalogService,
                appService
        );
        this.replacementsView = new ReplacementsTabView(
                configurationService,
                voiceCatalogService,
                appService
        );
        this.diagnosticsView = new DiagnosticsTabView(
                appService,
                configurationService,
                audioDeviceService
        );

        // Header
        HBox header = createHeader();
        rootLayout.setTop(header);

        // Tabs
        Tab tabLogs = new Tab("Live Chat Logs", chatLogsView);
        tabLogs.setClosable(false);

        Tab tabConfig = new Tab("Configuration & Voices", configurationView);
        tabConfig.setClosable(false);

        Tab tabReplacements = new Tab("String Replacements", replacementsView);
        tabReplacements.setClosable(false);

        Tab tabDiag = new Tab("Diagnostics & Console", diagnosticsView);
        tabDiag.setClosable(false);

        tabPane.getTabs().addAll(tabLogs, tabConfig, tabReplacements, tabDiag);
        rootLayout.setCenter(tabPane);
    }

    private HBox createHeader() {
        HBox header = new HBox(16);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        // Branding
        VBox titleBox = new VBox(2);
        Label titleLabel = new Label("EVE-TTS");
        titleLabel.getStyleClass().add("app-title");
        Label subTitle = new Label("Real-Time Voice Synthesizer & Log Monitor");
        subTitle.getStyleClass().add("app-subtitle");
        titleBox.getChildren().addAll(titleLabel, subTitle);

        // Status
        statusBadge.getStyleClass().addAll("status-badge", "status-badge-stopped");
        statusDetailLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        channelCountBadge.setStyle("-fx-background-color: #2c313a; -fx-text-fill: #8b949e; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 11px;");

        HBox statusBox = new HBox(10, statusBadge, statusDetailLabel, channelCountBadge);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Control Buttons
        startButton.getStyleClass().add("btn-success");
        startButton.setOnAction(e -> handleStart());

        stopButton.getStyleClass().add("btn-danger");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> handleStop());

        restartButton.setOnAction(e -> handleRestart());
        restartButton.setDisable(true);

        HBox buttonBox = new HBox(8, startButton, stopButton, restartButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(titleBox, statusBox, spacer, buttonBox);
        return header;
    }

    private void registerServiceListeners() {
        appService.addStateListener((newState, message) -> Platform.runLater(() -> {
            onStateChanged(newState, message);
        }));

        appService.addChatEventListener(event -> Platform.runLater(() -> {
            chatLogsView.addChatEvent(event);
            if (event.getMessage() != null) {
                String logText = String.format("[%s] %s > %s%s",
                        event.getChannelName(),
                        event.getSender(),
                        event.getMessage(),
                        event.getDetails() != null ? " (" + event.getDetails() + ")" : "");
                diagnosticsView.appendLog(logText);
            }
        }));
    }

    private void onStateChanged(ServiceState state, String message) {
        statusBadge.setText(state.name());
        statusBadge.getStyleClass().removeAll(
                "status-badge-running", "status-badge-stopped",
                "status-badge-starting", "status-badge-error"
        );

        switch (state) {
            case RUNNING -> {
                statusBadge.getStyleClass().add("status-badge-running");
                startButton.setDisable(true);
                stopButton.setDisable(false);
                restartButton.setDisable(false);
            }
            case STARTING -> {
                statusBadge.getStyleClass().add("status-badge-starting");
                startButton.setDisable(true);
                stopButton.setDisable(true);
                restartButton.setDisable(true);
            }
            case STOPPING -> {
                statusBadge.getStyleClass().add("status-badge-stopped");
                startButton.setDisable(true);
                stopButton.setDisable(true);
                restartButton.setDisable(true);
            }
            case ERROR -> {
                statusBadge.getStyleClass().add("status-badge-error");
                startButton.setDisable(false);
                stopButton.setDisable(true);
                restartButton.setDisable(false);
            }
            case STOPPED -> {
                statusBadge.getStyleClass().add("status-badge-stopped");
                startButton.setDisable(false);
                stopButton.setDisable(true);
                restartButton.setDisable(true);
            }
        }

        if (message != null && !message.trim().isEmpty()) {
            statusDetailLabel.setText(message);
        }

        diagnosticsView.updateServiceState(state, message);
        updateChannelCount();
    }

    public void updateChannelCount() {
        AppConfiguration config = configurationService.getConfiguration();
        int count = (config != null && config.getChannels() != null) ? config.getChannels().size() : 0;
        channelCountBadge.setText(String.format("Channels: %d", count));
    }

    public void handleStart() {
        startButton.setDisable(true);
        CompletableFuture.runAsync(() -> {
            try {
                appService.start();
            } catch (Exception e) {
                logger.error("Failed to start service: {}", e.getMessage(), e);
            }
        });
    }

    public void handleStop() {
        stopButton.setDisable(true);
        CompletableFuture.runAsync(appService::stop);
    }

    public void handleRestart() {
        restartButton.setDisable(true);
        CompletableFuture.runAsync(() -> {
            try {
                appService.restart();
            } catch (Exception e) {
                logger.error("Failed to restart service: {}", e.getMessage(), e);
            }
        });
    }

    public Parent getView() {
        return rootLayout;
    }
}
