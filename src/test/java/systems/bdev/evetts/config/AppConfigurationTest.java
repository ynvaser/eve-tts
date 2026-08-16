package systems.bdev.evetts.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigurationTest {

    @Test
    void testDefaultConfigurationFallbackRequiresCharacterVoices() {
        assertThrows(IllegalArgumentException.class, () -> {
            AppConfiguration.loadConfiguration("non_existent_config.json");
        }, "Loading non-existent configuration file with empty characterVoices should throw IllegalArgumentException");
    }

    @Test
    void testExpandPathEnvironmentVariables() {
        String userHome = System.getProperty("user.home");

        String winPath = "%USERPROFILE%\\Documents\\EVE\\logs\\Chatlogs";
        String expandedWin = AppConfiguration.expandPath(winPath);
        assertTrue(expandedWin.startsWith(userHome));
        assertFalse(expandedWin.contains("%USERPROFILE%"));

        String tildePath = "~/Documents/EVE/logs/Chatlogs";
        String expandedTilde = AppConfiguration.expandPath(tildePath);
        assertTrue(expandedTilde.startsWith(userHome));
        assertFalse(expandedTilde.startsWith("~"));

        String unixPath = "$HOME/Documents/EVE/logs/Chatlogs";
        String expandedUnix = AppConfiguration.expandPath(unixPath);
        assertTrue(expandedUnix.startsWith(userHome));
        assertFalse(expandedUnix.contains("$HOME"));
    }

    @Test
    void testLoadCustomConfigFile(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.json");
        String json = """
                {
                  "eveLogDirectory": "%USERPROFILE%/Documents/EVE/logs/Chatlogs",
                  "channels": ["Degen Gambling", "CorpChat"],
                  "pollIntervalMs": 250,
                  "characterVoices": {
                    "Davos Skyworth": "am_adam"
                  }
                }
                """;
        Files.writeString(configFile, json);

        AppConfiguration config = AppConfiguration.loadConfiguration(configFile.toString());

        String userHome = System.getProperty("user.home");
        assertTrue(config.getEveLogDirectory().startsWith(userHome));
        assertEquals(2, config.getChannels().size());
        assertTrue(config.getChannels().contains("Degen Gambling"));
        assertTrue(config.getChannels().contains("CorpChat"));

        assertEquals(250L, config.getPollIntervalMs());
    }

    @Test
    void testLoadTtsAndCharacterVoicesConfiguration(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.json");
        String json = """
                {
                  "enableTts": true,
                  "audioDevice": "CABLE Input",
                  "defaultVoice": "af_sarah",
                  "characterVoices": {
                    "Davos Skyworth": "am_adam",
                    "TestPilot": "af_bella"
                  }
                }
                """;
        Files.writeString(configFile, json);

        AppConfiguration config = AppConfiguration.loadConfiguration(configFile.toString());

        assertTrue(config.isEnableTts());
        assertFalse(config.isUseGpu());
        assertEquals("CABLE Input", config.getAudioDevice());
        assertNotNull(config.getCharacterVoices());
        assertEquals(2, config.getCharacterVoices().size());
        assertEquals("am_adam", config.getCharacterVoices().get("Davos Skyworth"));
        assertEquals("af_bella", config.getCharacterVoices().get("TestPilot"));
    }

    @Test
    void testEmptyCharacterVoicesThrowsException(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("empty_voices_config.json");
        String json = """
                {
                  "enableTts": true,
                  "characterVoices": {}
                }
                """;
        Files.writeString(configFile, json);

        assertThrows(IllegalArgumentException.class, () -> AppConfiguration.loadConfiguration(configFile.toString()));
    }

    @Test
    void testTtsEngineConfigurationLoading(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("piper_config.json");
        String json = """
                {
                  "enableTts": true,
                  "ttsEngine": "piper",
                  "characterVoices": {
                    "Davos Skyworth": "ryan"
                  }
                }
                """;
        Files.writeString(configFile, json);

        AppConfiguration config = AppConfiguration.loadConfiguration(configFile.toString());
        assertEquals("piper", config.getTtsEngine());
    }
}
