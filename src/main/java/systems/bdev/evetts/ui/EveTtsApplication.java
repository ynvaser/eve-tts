package systems.bdev.evetts.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;
import systems.bdev.evetts.service.impl.DefaultAppService;
import systems.bdev.evetts.service.impl.DefaultAudioDeviceService;
import systems.bdev.evetts.service.impl.DefaultVoiceCatalogService;
import systems.bdev.evetts.service.impl.JsonConfigurationService;
import systems.bdev.evetts.ui.controller.MainViewController;

import java.net.URL;

/**
 * Composition Root and JavaFX Application entry point for EVE-TTS.
 */
public class EveTtsApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(EveTtsApplication.class);

    private AppService appService;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Initializing EVE-TTS JavaFX UI Composition Root...");

        // 1. Composition Root: Instantiate Services
        ConfigurationService configurationService = new JsonConfigurationService();
        AudioDeviceService audioDeviceService = new DefaultAudioDeviceService();
        VoiceCatalogService voiceCatalogService = new DefaultVoiceCatalogService();

        this.appService = new DefaultAppService(
                configurationService,
                audioDeviceService,
                voiceCatalogService
        );

        // 2. Instantiate Main Controller & View
        MainViewController controller = new MainViewController(
                appService,
                configurationService,
                audioDeviceService,
                voiceCatalogService
        );

        // 3. Configure Scene & Theme
        Scene scene = new Scene(controller.getView(), 1120, 760);

        URL cssResource = getClass().getResource("/styles/theme.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            logger.warn("theme.css stylesheet not found on classpath!");
        }

        primaryStage.setTitle("EVE-TTS - Voice Synthesizer & Log Monitor");
        primaryStage.setMinWidth(860);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);

        // 4. Graceful Shutdown Hook
        primaryStage.setOnCloseRequest(event -> {
            logger.info("Application window close requested. Shutting down background services...");
            if (appService != null) {
                appService.stop();
            }
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
        logger.info("EVE-TTS JavaFX UI displayed successfully.");
    }

    @Override
    public void stop() {
        if (appService != null) {
            appService.stop();
        }
    }
}
