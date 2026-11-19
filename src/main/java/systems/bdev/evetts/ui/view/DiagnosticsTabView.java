package systems.bdev.evetts.ui.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.model.ServiceState;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View component for System Diagnostics, Hardware Status, and Console Output.
 */
public class DiagnosticsTabView extends ScrollPane {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppService appService;
    private final ConfigurationService configurationService;
    private final AudioDeviceService audioDeviceService;

    private final Label stateLabel = new Label("STOPPED");
    private final Label logDirStatusLabel = new Label("Checking...");
    private final Label audioDeviceStatusLabel = new Label("Checking...");
    private final Label runtimeInfoLabel = new Label();
    private final TextArea consoleTextArea = new TextArea();

    public DiagnosticsTabView(
            AppService appService,
            ConfigurationService configurationService,
            AudioDeviceService audioDeviceService
    ) {
        this.appService = appService;
        this.configurationService = configurationService;
        this.audioDeviceService = audioDeviceService;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent;");

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(12, 16, 12, 16));

        contentBox.getChildren().addAll(
                createDiagnosticsCards(),
                createConsoleCard()
        );

        setContent(contentBox);
        refreshDiagnostics();
    }

    private VBox createDiagnosticsCards() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("System Diagnostics & Hardware Status");
        cardTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        // Runtime Info
        String javaVer = System.getProperty("java.version", "Unknown");
        String osName = System.getProperty("os.name", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        runtimeInfoLabel.setText(String.format("Java %s on %s (%s)", javaVer, osName, osArch));
        runtimeInfoLabel.setStyle("-fx-text-fill: #e6edf3;");

        grid.add(createFieldLabel("Runtime Environment:"), 0, 0);
        grid.add(runtimeInfoLabel, 1, 0);

        // Core State
        stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #8b949e;");
        grid.add(createFieldLabel("Core Service State:"), 0, 1);
        grid.add(stateLabel, 1, 1);

        // EVE Log Directory Status
        grid.add(createFieldLabel("Log Directory Status:"), 0, 2);
        grid.add(logDirStatusLabel, 1, 2);

        // Audio Device Status
        grid.add(createFieldLabel("Audio Device Status:"), 0, 3);
        grid.add(audioDeviceStatusLabel, 1, 3);

        Button refreshBtn = new Button("Refresh Diagnostics");
        refreshBtn.setOnAction(e -> refreshDiagnostics());

        card.getChildren().addAll(cardTitle, grid, refreshBtn);
        return card;
    }

    private VBox createConsoleCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label cardTitle = new Label("Diagnostics Console");
        cardTitle.getStyleClass().add("card-title");
        HBox.setHgrow(cardTitle, Priority.ALWAYS);

        Button clearBtn = new Button("Clear Console");
        clearBtn.setOnAction(e -> consoleTextArea.clear());

        header.getChildren().addAll(cardTitle, clearBtn);

        consoleTextArea.setEditable(false);
        consoleTextArea.setWrapText(true);
        consoleTextArea.setPrefHeight(320);
        consoleTextArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");

        card.getChildren().addAll(header, consoleTextArea);
        return card;
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: 500;");
        return label;
    }

    public void updateServiceState(ServiceState state, String message) {
        Platform.runLater(() -> {
            stateLabel.setText(state.name() + (message != null ? " - " + message : ""));
            switch (state) {
                case RUNNING -> stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #49aa52;");
                case STARTING -> stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #00bcd4;");
                case ERROR -> stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e06c75;");
                default -> stateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #8b949e;");
            }
            appendLog(String.format("[STATE] %s: %s", state, message != null ? message : ""));
        });
    }

    public void refreshDiagnostics() {
        AppConfiguration config = configurationService.getConfiguration();
        if (config == null) return;

        // Check Directory
        String logDir = config.getEveLogDirectory();
        if (logDir == null || logDir.trim().isEmpty()) {
            logDirStatusLabel.setText("Not configured");
            logDirStatusLabel.setStyle("-fx-text-fill: #e06c75;");
        } else {
            File dir = new File(logDir);
            if (dir.exists() && dir.isDirectory()) {
                logDirStatusLabel.setText(String.format("Online (%s)", dir.getAbsolutePath()));
                logDirStatusLabel.setStyle("-fx-text-fill: #49aa52;");
            } else {
                logDirStatusLabel.setText(String.format("Directory not found: %s", dir.getAbsolutePath()));
                logDirStatusLabel.setStyle("-fx-text-fill: #e5c07b;");
            }
        }

        // Check Audio Device
        String audioDev = config.getAudioDevice();
        List<String> availableDevices = audioDeviceService.getAvailableAudioDevices();
        if (audioDev == null || audioDev.trim().isEmpty()) {
            if (!availableDevices.isEmpty()) {
                audioDeviceStatusLabel.setText(String.format("Default (First Detected: '%s')", availableDevices.get(0)));
                audioDeviceStatusLabel.setStyle("-fx-text-fill: #49aa52;");
            } else {
                audioDeviceStatusLabel.setText("No audio devices detected");
                audioDeviceStatusLabel.setStyle("-fx-text-fill: #e06c75;");
            }
        } else {
            boolean available = audioDeviceService.isDeviceAvailable(audioDev);
            if (available) {
                audioDeviceStatusLabel.setText(String.format("Available: '%s'", audioDev));
                audioDeviceStatusLabel.setStyle("-fx-text-fill: #49aa52;");
            } else {
                audioDeviceStatusLabel.setText(String.format("Unavailable / Disconnected: '%s'", audioDev));
                audioDeviceStatusLabel.setStyle("-fx-text-fill: #e06c75;");
            }
        }
    }

    public void appendLog(String message) {
        if (message == null) return;
        Platform.runLater(() -> {
            String time = LocalDateTime.now().format(TIME_FORMATTER);
            consoleTextArea.appendText(String.format("[%s] %s%n", time, message));
        });
    }
}
