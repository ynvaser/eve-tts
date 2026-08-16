package systems.bdev.evetts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runner that initializes and executes LogWatcherService instances across all configured channels.
 */
public class LogWatcherServiceRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogWatcherServiceRunner.class);

    private final AppConfiguration configuration;
    private final LogMessageProcessService processService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;

    public LogWatcherServiceRunner(AppConfiguration configuration, LogMessageProcessService processService) {
        this.configuration = configuration;
        this.processService = processService;
    }

    public synchronized void start() {
        if (running.get()) {
            logger.warn("LogWatcherServiceRunner is already running.");
            return;
        }

        List<String> channels = configuration.getChannels();
        if (channels == null || channels.isEmpty()) {
            logger.error("No channels configured to watch.");
            return;
        }

        running.set(true);
        executorService = Executors.newFixedThreadPool(channels.size());
        logger.info("Starting log watchers for {} channel(s): {}", channels.size(), channels);

        for (String channel : channels) {
            String channelName = channel.trim();
            if (channelName.isEmpty()) {
                continue;
            }

            LogWatcherService watcher = new LogWatcherService(
                    configuration.getEveLogDirectory(),
                    processService,
                    configuration.getPollIntervalMs()
            );

            executorService.submit(() -> watcher.runWatcher(channelName, running));
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        logger.info("Stopping LogWatcherServiceRunner...");
        running.set(false);

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("LogWatcherServiceRunner stopped cleanly.");
    }
}
