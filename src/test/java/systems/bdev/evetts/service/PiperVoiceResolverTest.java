package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import org.pitest.voices.Model;

import static org.junit.jupiter.api.Assertions.*;

class PiperVoiceResolverTest {

    private final VoiceResolver resolver = new PiperVoiceResolver();

    @Test
    void testResolvePiperKnownVoices() {
        Model alba = resolver.resolveVoice("alba");
        assertNotNull(alba);

        Model ryan = resolver.resolveVoice("ryan");
        assertNotNull(ryan);

        Model lessac = resolver.resolveVoice("lessac");
        assertNotNull(lessac);

        Model bryce = resolver.resolveVoice("bryce");
        assertNotNull(bryce);

        Model hfcMale = resolver.resolveVoice("hfcMaleMedium");
        assertNotNull(hfcMale);

        Model hfcFemale = resolver.resolveVoice("hfc_female");
        assertNotNull(hfcFemale);
    }

    @Test
    void testResolveUnknownPiperVoiceFallsBackToAlba() {
        Model unknown = resolver.resolveVoice("unknown_piper_voice_xyz");
        assertNotNull(unknown);
    }
}
