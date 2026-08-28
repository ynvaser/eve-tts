package systems.bdev.evetts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.api.ConfigurationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Implementation of ConfigurationService backed by JSON file serialization.
 */
public class JsonConfigurationService implements ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(JsonConfigurationService.class);
    private static final String DEFAULT_CONFIG_FILE = "config.json";

    private final String configFilePath;
    private final ObjectMapper objectMapper;
    private volatile AppConfiguration currentConfiguration;

    public JsonConfigurationService() {
        this(DEFAULT_CONFIG_FILE);
    }

    public JsonConfigurationService(String configFilePath) {
        this.configFilePath = (configFilePath != null && !configFilePath.trim().isEmpty())
                ? configFilePath.trim()
                : DEFAULT_CONFIG_FILE;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            reloadConfiguration();
        } catch (IOException e) {
            logger.warn("Could not load configuration from {}. Initializing default config: {}", this.configFilePath, e.getMessage());
            this.currentConfiguration = createDefaultConfiguration();
        }
    }

    @Override
    public AppConfiguration getConfiguration() {
        return currentConfiguration;
    }

    @Override
    public synchronized void updateConfiguration(AppConfiguration config) {
        this.currentConfiguration = Objects.requireNonNull(config, "Configuration cannot be null");
    }

    @Override
    public synchronized void saveConfiguration() throws IOException {
        if (currentConfiguration == null) {
            throw new IllegalStateException("No active configuration to save.");
        }

        Path targetPath = Paths.get(configFilePath);
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Atomic write semantics: write to temp file then atomic move/replace
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".tmp");
        objectMapper.writeValue(tempPath.toFile(), currentConfiguration);
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Fallback for filesystems that do not support ATOMIC_MOVE
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        logger.info("Configuration successfully saved to {}", targetPath.toAbsolutePath());
    }

    @Override
    public synchronized void reloadConfiguration() throws IOException {
        File file = new File(configFilePath);
        if (file.exists() && file.isFile()) {
            this.currentConfiguration = AppConfiguration.loadConfiguration(configFilePath);
            logger.info("Configuration loaded successfully from {}", file.getAbsolutePath());
        } else {
            logger.info("Configuration file {} does not exist. Creating default configuration.", configFilePath);
            this.currentConfiguration = createDefaultConfiguration();
            saveConfiguration();
        }
    }

    @Override
    public AppConfiguration createDefaultConfiguration() {
        AppConfiguration config = new AppConfiguration();
        config.setEveLogDirectory(Paths.get(System.getProperty("user.home"), "Documents", "EVE", "logs", "Chatlogs").toString());
        config.setChannels(new java.util.ArrayList<>(java.util.List.of("Fleet")));
        config.setTtsEngine("kokoro");
        config.setAudioDevice("CABLE Input");
        config.setEnableTts(true);
        config.setUseGpu(false);
        config.setPollIntervalMs(500L);
        config.setCharacterVoices(new java.util.HashMap<>(java.util.Map.of("Davos Skyworth", "am_michael")));
        return config;
    }
}
