package systems.bdev.evetts.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application configuration loaded from config.json with environment variable path expansion
 * and UTC start timestamp generation to match EVE Online game time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AppConfiguration.class);
    public static final DateTimeFormatter EVE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    private static final Pattern WIN_ENV_PATTERN = Pattern.compile("%([^%]+)%");
    private static final Pattern UNIX_ENV_PATTERN = Pattern.compile("\\$(\\{([^}]+)\\}|([a-zA-Z_][a-zA-Z0-9_]*))");

    @JsonProperty("eveLogDirectory")
    @JsonAlias({"EveLogDirectory", "eveLogDirectory"})
    private String eveLogDirectory;

    @JsonProperty("channels")
    @JsonAlias({"channels", "Channels", "ChatChannelNames", "chatChannelNames"})
    private List<String> channels = new ArrayList<>();

    @JsonProperty("pollIntervalMs")
    @JsonAlias({"PollIntervalMs", "pollIntervalMs"})
    private long pollIntervalMs = 500L;

    @JsonProperty("startFromTimestamp")
    @JsonAlias({"StartFromTimestamp", "startFromTimestamp"})
    private String startFromTimestamp;

    @JsonProperty("enableTts")
    @JsonAlias({"enableTts", "EnableTts"})
    private boolean enableTts = true;

    @JsonProperty("ttsEngine")
    @JsonAlias({"ttsEngine", "TtsEngine", "engine"})
    private String ttsEngine = "kokoro";

    @JsonProperty("useGpu")
    @JsonAlias({"useGpu", "UseGpu", "enableGpu", "gpu"})
    private boolean useGpu = false;

    @JsonProperty("audioDevice")
    @JsonAlias({"audioDevice", "AudioDevice"})
    private String audioDevice = "CABLE Input";

    @JsonProperty("characterVoices")
    @JsonAlias({"characterVoices", "CharacterVoices", "voiceMap"})
    private Map<String, String> characterVoices = new HashMap<>();

    public AppConfiguration() {
    }

    public static AppConfiguration loadConfiguration(String configFilePath) {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File(configFilePath);

        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("Loading configuration from: {}", configFile.getAbsolutePath());
                AppConfiguration config = mapper.readValue(configFile, AppConfiguration.class);
                config.applyDefaultsIfNeeded();
                return config;
            } catch (IOException e) {
                logger.warn("Could not read configuration file {}, falling back to defaults: {}", configFilePath, e.getMessage());
            }
        } else {
            logger.info("Configuration file '{}' not found, using default configuration.", configFilePath);
        }

        AppConfiguration config = new AppConfiguration();
        config.applyDefaultsIfNeeded();
        return config;
    }

    private void applyDefaultsIfNeeded() {
        if (eveLogDirectory == null || eveLogDirectory.trim().isEmpty()) {
            String userHome = System.getProperty("user.home");
            eveLogDirectory = Paths.get(userHome, "Documents", "EVE", "logs", "Chatlogs").toString();
        }

        if (channels == null || channels.isEmpty()) {
            channels = new ArrayList<>();
            channels.add("Fleet");
        }

        if (ttsEngine == null || ttsEngine.trim().isEmpty()) {
            ttsEngine = "kokoro";
        } else {
            ttsEngine = ttsEngine.trim().toLowerCase();
        }

        if (characterVoices == null || characterVoices.isEmpty()) {
            logger.error("Configuration error: 'characterVoices' is empty. At least one character voice mapping is required in config.json.");
            throw new IllegalArgumentException("Configuration error: 'characterVoices' is required and cannot be empty.");
        }

        if (startFromTimestamp == null || startFromTimestamp.trim().isEmpty()) {
            // EVE Online chat logs use UTC time (EVE Time)
            startFromTimestamp = LocalDateTime.now(ZoneOffset.UTC).format(EVE_TIMESTAMP_FORMATTER);
        }
    }

    /**
     * Expands environment variables (%USERPROFILE%, $HOME, ~/ etc.) in directory paths.
     */
    public static String expandPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        String result = path.trim();

        // 1. Expand ~ to user home
        if (result.startsWith("~")) {
            String userHome = System.getProperty("user.home");
            result = userHome + result.substring(1);
        }

        // 2. Expand Windows environment variables: %VAR%
        Matcher winMatcher = WIN_ENV_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (winMatcher.find()) {
            String envVar = winMatcher.group(1);
            String envValue = System.getenv(envVar);
            if (envValue == null) {
                if ("USERPROFILE".equalsIgnoreCase(envVar) || "HOME".equalsIgnoreCase(envVar)) {
                    envValue = System.getProperty("user.home");
                } else {
                    envValue = winMatcher.group(0);
                }
            }
            winMatcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }
        winMatcher.appendTail(sb);
        result = sb.toString();

        // 3. Expand Unix environment variables: ${VAR} or $VAR
        Matcher unixMatcher = UNIX_ENV_PATTERN.matcher(result);
        sb = new StringBuilder();
        while (unixMatcher.find()) {
            String envVar = unixMatcher.group(2) != null ? unixMatcher.group(2) : unixMatcher.group(3);
            String envValue = System.getenv(envVar);
            if (envValue == null) {
                if ("HOME".equalsIgnoreCase(envVar) || "USERPROFILE".equalsIgnoreCase(envVar)) {
                    envValue = System.getProperty("user.home");
                } else {
                    envValue = unixMatcher.group(0);
                }
            }
            unixMatcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }
        unixMatcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    public String getEveLogDirectory() {
        return expandPath(eveLogDirectory);
    }

    public void setEveLogDirectory(String eveLogDirectory) {
        this.eveLogDirectory = eveLogDirectory;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getStartFromTimestamp() {
        return startFromTimestamp;
    }

    public void setStartFromTimestamp(String startFromTimestamp) {
        this.startFromTimestamp = startFromTimestamp;
    }

    public boolean isEnableTts() {
        return enableTts;
    }

    public void setEnableTts(boolean enableTts) {
        this.enableTts = enableTts;
    }

    public String getTtsEngine() {
        return ttsEngine != null ? ttsEngine.toLowerCase() : "kokoro";
    }

    public void setTtsEngine(String ttsEngine) {
        this.ttsEngine = ttsEngine;
    }

    public boolean isUseGpu() {
        return useGpu;
    }

    public void setUseGpu(boolean useGpu) {
        this.useGpu = useGpu;
    }

    public String getAudioDevice() {
        return audioDevice;
    }

    public void setAudioDevice(String audioDevice) {
        this.audioDevice = audioDevice;
    }

    public Map<String, String> getCharacterVoices() {
        return characterVoices;
    }

    public void setCharacterVoices(Map<String, String> characterVoices) {
        this.characterVoices = characterVoices != null ? characterVoices : new HashMap<>();
    }
}
