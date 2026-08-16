package systems.bdev.evetts.service;

import org.pitest.voices.Model;
import org.pitest.voices.kokoro.KokoroModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps voice configuration strings to Kokoro voice model instances.
 */
public class KokoroVoiceResolver implements VoiceResolver {

    private static final Logger logger = LoggerFactory.getLogger(KokoroVoiceResolver.class);

    @Override
    public Model resolveVoice(String voiceName) {
        return resolveVoiceStatic(voiceName);
    }

    public static Model resolveVoiceStatic(String voiceName) {
        if (voiceName == null || voiceName.trim().isEmpty()) {
            return KokoroModels.afSarah();
        }

        String normalized = voiceName.trim().toLowerCase().replace("-", "_");

        switch (normalized) {
            case "af_sarah":
            case "sarah":
                return KokoroModels.afSarah();
            case "af_bella":
            case "bella":
                return KokoroModels.afBella();
            case "af_nicole":
            case "nicole":
                return KokoroModels.afNicole();
            case "af_sky":
            case "sky":
                return KokoroModels.afSky();
            case "am_adam":
            case "adam":
                return KokoroModels.amAdam();
            case "am_michael":
            case "michael":
                return KokoroModels.amMichael();
            case "bf_emma":
            case "emma":
                return KokoroModels.bfEmma();
            case "bf_isabella":
            case "isabella":
                return KokoroModels.bfIsabella();
            case "bm_george":
            case "george":
                return KokoroModels.bmGeorge();
            case "bm_fable":
            case "fable":
                return KokoroModels.bmGeorge();
            case "af":
                return KokoroModels.af();
            default:
                logger.warn("Unknown Kokoro voice model '{}'. Falling back to 'af_sarah'.", voiceName);
                return KokoroModels.afSarah();
        }
    }
}
