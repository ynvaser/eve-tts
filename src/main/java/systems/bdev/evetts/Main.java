package systems.bdev.evetts;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.ui.EveTtsApplication;

/**
 * Main application launcher for EVE-TTS.
 * Launches the JavaFX GUI application.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in thread [{}]: {}", thread.getName(), throwable.getMessage(), throwable);
        });

        logger.info("Launching EVE-TTS Desktop GUI...");
        Application.launch(EveTtsApplication.class, args);
    }
}
