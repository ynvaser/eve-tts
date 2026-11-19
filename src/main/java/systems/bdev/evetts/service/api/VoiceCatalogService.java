package systems.bdev.evetts.service.api;

import java.util.List;

/**
 * Service contract for discovering supported TTS engines and available voice models.
 */
public interface VoiceCatalogService {

    List<String> getSupportedEngines();

    List<String> getAvailableVoices(String engine);

    String getDefaultVoice(String engine);
}
