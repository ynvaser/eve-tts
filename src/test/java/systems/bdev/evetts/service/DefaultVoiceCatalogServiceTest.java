package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import systems.bdev.evetts.service.impl.DefaultVoiceCatalogService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultVoiceCatalogServiceTest {

    private final DefaultVoiceCatalogService catalogService = new DefaultVoiceCatalogService();

    @Test
    void testSupportedEngines() {
        List<String> engines = catalogService.getSupportedEngines();
        assertNotNull(engines);
        assertTrue(engines.contains("kokoro"));
        assertTrue(engines.contains("piper"));
    }

    @Test
    void testKokoroVoices() {
        List<String> voices = catalogService.getAvailableVoices("kokoro");
        assertNotNull(voices);
        assertTrue(voices.contains("af_sarah"));
        assertTrue(voices.contains("am_adam"));
        assertTrue(voices.contains("am_michael"));
        assertEquals("af_sarah", catalogService.getDefaultVoice("kokoro"));
    }

    @Test
    void testPiperVoices() {
        List<String> voices = catalogService.getAvailableVoices("piper");
        assertNotNull(voices);
        assertTrue(voices.contains("alba"));
        assertTrue(voices.contains("bryce"));
        assertTrue(voices.contains("ryan"));
        assertEquals("alba", catalogService.getDefaultVoice("piper"));
    }
}
