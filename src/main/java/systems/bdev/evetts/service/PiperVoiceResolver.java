package systems.bdev.evetts.service;

import org.pitest.voices.Model;
import org.pitest.voices.alba.Alba;
import org.pitest.voices.download.Models;
import org.pitest.voices.download.NonEnglishModels;
import org.pitest.voices.download.UsModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps voice configuration strings to Piper voice model instances.
 */
public class PiperVoiceResolver implements VoiceResolver {

    private static final Logger logger = LoggerFactory.getLogger(PiperVoiceResolver.class);

    @Override
    public Model resolveVoice(String voiceName) {
        if (voiceName == null || voiceName.trim().isEmpty()) {
            return Alba.albaMedium();
        }

        String normalized = voiceName.trim().toLowerCase().replace("-", "_");

        switch (normalized) {
            // US Models (UsModels)
            case "bryce":
            case "bryce_medium":
            case "brycemedium":
                return UsModels.bryceMedium();
            case "hfcfemale":
            case "hfc_female":
            case "hfc_female_medium":
            case "hfcfemalemedium":
                return UsModels.hfcFemaleMedium();
            case "hfcmale":
            case "hfc_male":
            case "hfc_male_medium":
            case "hfcmalemedium":
                return UsModels.hfcMaleMedium();
            case "joe":
            case "joe_medium":
            case "joemedium":
                return UsModels.joeMedium();
            case "john":
            case "john_medium":
            case "johnmedium":
                return UsModels.johnMedium();
            case "kristin":
            case "kristin_medium":
            case "kristinmedium":
                return UsModels.kristinMedium();
            case "kusal":
            case "kusal_medium":
            case "kusalmedium":
                return UsModels.kusalMedium();
            case "lessac":
            case "lessac_high":
            case "lessachigh":
                return UsModels.lessacHigh();
            case "norman":
            case "norman_medium":
            case "normanmedium":
                return UsModels.normanMedium();
            case "ryan":
            case "ryan_high":
            case "ryanhigh":
                return UsModels.ryanHigh();
            case "sam":
            case "sam_medium":
            case "sammedium":
                return UsModels.samMedium();

            // General Models (Models)
            case "alba":
            case "alba_medium":
            case "albamedium":
                return Alba.albaMedium();
            case "cori":
            case "cori_high":
            case "corihigh":
                return Models.coriHigh();
            case "aru":
            case "aru_medium":
            case "arumedium":
                return Models.aruMedium(0);
            case "alan":
            case "alan_medium":
            case "alanmedium":
                return Models.alanMedium();
            case "jenny":
            case "jenny_dioco":
            case "jenny_dioco_medium":
            case "jennydiocomedium":
                return Models.jennyDiocoMedium();
            case "amy":
            case "sweetbbak_amy":
            case "sweetbbakamy":
                return Models.sweetbbakAmy();
            case "northern_english_male":
            case "northernenglishmale":
            case "northernenglish":
                return Models.northernEnglishMale();

            // Non-English Models (NonEnglishModels)
            case "fr_fr_siwis":
            case "fr_siwis":
            case "siwis":
                return NonEnglishModels.frFRSiwis();
            case "nl_nl_ronnie":
            case "nl_ronnie":
            case "ronnie":
                return NonEnglishModels.nlNLRonnie();

            default:
                logger.warn("Unknown Piper voice model '{}'. Falling back to 'alba'.", voiceName);
                return Alba.albaMedium();
        }
    }
}
