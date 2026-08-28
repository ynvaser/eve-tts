package systems.bdev.evetts.service.impl;

import systems.bdev.evetts.service.api.VoiceCatalogService;

import java.util.Collections;
import java.util.List;

/**
 * Default implementation of VoiceCatalogService listing supported TTS engines and their voices.
 */
public class DefaultVoiceCatalogService implements VoiceCatalogService {

    private static final List<String> SUPPORTED_ENGINES = List.of("kokoro", "piper");

    private static final List<String> KOKORO_VOICES = List.of(
            "af_sarah",
            "af_bella",
            "af_nicole",
            "af_sky",
            "am_adam",
            "am_michael",
            "bf_emma",
            "bf_isabella",
            "bm_george",
            "bm_fable",
            "af"
    );

    private static final List<String> PIPER_VOICES = List.of(
            "alba",
            "bryce",
            "hfc_female",
            "hfc_male",
            "joe",
            "john",
            "kristin",
            "kusal",
            "lessac",
            "norman",
            "ryan",
            "sam",
            "cori",
            "aru",
            "alan",
            "jenny",
            "amy",
            "northern_english_male",
            "fr_fr_siwis",
            "nl_nl_ronnie"
    );

    @Override
    public List<String> getSupportedEngines() {
        return SUPPORTED_ENGINES;
    }

    @Override
    public List<String> getAvailableVoices(String engine) {
        if (engine == null) {
            return KOKORO_VOICES;
        }
        if ("piper".equalsIgnoreCase(engine.trim())) {
            return PIPER_VOICES;
        }
        return KOKORO_VOICES;
    }

    @Override
    public String getDefaultVoice(String engine) {
        if (engine != null && "piper".equalsIgnoreCase(engine.trim())) {
            return "alba";
        }
        return "af_sarah";
    }
}
