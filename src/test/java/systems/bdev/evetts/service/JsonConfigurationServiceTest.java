package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.service.impl.JsonConfigurationService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigurationServiceTest {

    @Test
    void testSaveAndLoadConfiguration(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("config.json");
        JsonConfigurationService service = new JsonConfigurationService(configPath.toString());

        AppConfiguration config = service.getConfiguration();
        assertNotNull(config);

        config.setEveLogDirectory(tempDir.toString());
        config.setChannels(List.of("Fleet", "Intel"));
        config.setTtsEngine("piper");
        config.setAudioDevice("Virtual Audio Cable");
        config.setEnableTts(true);
        config.setUseGpu(true);
        config.setPollIntervalMs(250L);
        config.setCharacterVoices(Map.of("Davos", "bryce"));

        service.updateConfiguration(config);
        service.saveConfiguration();

        // Create fresh service instance pointing to same file
        JsonConfigurationService reloadedService = new JsonConfigurationService(configPath.toString());
        AppConfiguration reloaded = reloadedService.getConfiguration();

        assertNotNull(reloaded);
        assertEquals(tempDir.toString(), reloaded.getEveLogDirectory());
        assertEquals(List.of("Fleet", "Intel"), reloaded.getChannels());
        assertEquals("piper", reloaded.getTtsEngine());
        assertEquals("Virtual Audio Cable", reloaded.getAudioDevice());
        assertTrue(reloaded.isEnableTts());
        assertTrue(reloaded.isUseGpu());
        assertEquals(250L, reloaded.getPollIntervalMs());
        assertEquals("bryce", reloaded.getCharacterVoices().get("Davos"));
    }

    @Test
    void testCreateDefaultConfiguration(@TempDir Path tempDir) {
        Path dummyPath = tempDir.resolve("dummy_config.json");
        JsonConfigurationService service = new JsonConfigurationService(dummyPath.toString());
        AppConfiguration def = service.createDefaultConfiguration();

        assertNotNull(def);
        assertNotNull(def.getEveLogDirectory());
        assertFalse(def.getChannels().isEmpty());
        assertEquals("kokoro", def.getTtsEngine());
        assertFalse(def.getCharacterVoices().isEmpty());
    }
}
