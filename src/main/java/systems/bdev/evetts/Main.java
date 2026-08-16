package systems.bdev.evetts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.LogMessageProcessService;
import systems.bdev.evetts.service.LogWatcherServiceRunner;
import systems.bdev.evetts.service.TtsSpeechService;
import systems.bdev.evetts.service.UserDataService;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "config.json";
        AppConfiguration config;
        try {
            config = AppConfiguration.loadConfiguration(configPath);
        } catch (Exception e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
            System.exit(1);
            return;
        }

        logger.info("==================================================");
        logger.info("  EVE-TTS Service [Engine: {}]", config.getTtsEngine().toUpperCase());
        logger.info("==================================================");

        logger.info("Filtering log lines starting from timestamp: {}", config.getStartFromTimestamp());
        logger.info("Character voice mappings enabled for: {}", config.getCharacterVoices().keySet());

        UserDataService userDataService = new UserDataService();
        TtsSpeechService ttsSpeechService;

        try {
            ttsSpeechService = new TtsSpeechService(config);
        } catch (Exception e) {
            logger.error("Initialization failed: {}", e.getMessage());
            System.exit(1);
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
