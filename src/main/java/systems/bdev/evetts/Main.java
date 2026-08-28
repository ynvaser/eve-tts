package systems.bdev.evetts;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.LogMessageProcessService;
import systems.bdev.evetts.service.LogWatcherServiceRunner;
import systems.bdev.evetts.service.TtsSpeechService;
import systems.bdev.evetts.service.UserDataService;
import systems.bdev.evetts.ui.EveTtsApplication;

import java.util.Arrays;

/**
 * Main application launcher for EVE-TTS.
 * Launches JavaFX GUI by default, or headless CLI mode if --cli flag is specified.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread [{}]: {}", thread.getName(), throwable.getMessage(), throwable);
        });

        boolean cliMode = Arrays.stream(args).anyMatch(arg -> "--cli".equalsIgnoreCase(arg) || "--headless".equalsIgnoreCase(arg));

        if (cliMode) {
            runCliMode(args);
        } else {
            logger.info("Launching EVE-TTS Desktop GUI...");
            Application.launch(EveTtsApplication.class, args);
        }
    }

    private static void runCliMode(String[] args) {
        String configPath = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : "config.json";
        AppConfiguration config;
        try {
            config = AppConfiguration.loadConfiguration(configPath);
        } catch (Exception e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
            return;
        }

        logger.info("==================================================");
        logger.info("  EVE-TTS Service [Engine: {}] (CLI Mode)", config.getTtsEngine().toUpperCase());
        logger.info("==================================================");

        logger.info("Filtering log lines starting from timestamp: {}", config.getStartFromTimestamp());
        logger.info("Character voice mappings enabled for: {}", config.getCharacterVoices().keySet());

        UserDataService userDataService = new UserDataService();
        TtsSpeechService ttsSpeechService;

        try {
            ttsSpeechService = new TtsSpeechService(config);
        } catch (Exception e) {
            logger.error("Initialization failed: {}", e.getMessage());
            return;
        }

        LogMessageProcessService processService = new LogMessageProcessService(
                ttsSpeechService,
                userDataService,
                config.getStartFromTimestamp()
        );

        LogWatcherServiceRunner runner = new LogWatcherServiceRunner(config, processService);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received. Shutting down...");
            runner.stop();
            ttsSpeechService.close();
            userDataService.save();
        }));

        runner.start();
    }
}
