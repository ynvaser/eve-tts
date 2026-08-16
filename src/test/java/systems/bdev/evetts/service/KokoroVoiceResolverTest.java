package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import org.pitest.voices.Model;

import static org.junit.jupiter.api.Assertions.*;

class KokoroVoiceResolverTest {

    private final VoiceResolver resolver = new KokoroVoiceResolver();

    @Test
    void testResolveKnownVoiceAliases() {
        Model sarah = resolver.resolveVoice("af_sarah");
        assertNotNull(sarah, "af_sarah should resolve to a valid Kokoro model");

        Model bella = resolver.resolveVoice("af_bella");
        assertNotNull(bella, "af_bella should resolve to a valid Kokoro model");

        Model adam = resolver.resolveVoice("am_adam");
        assertNotNull(adam, "am_adam should resolve to a valid Kokoro model");

        Model george = resolver.resolveVoice("bm_george");
        assertNotNull(george, "bm_george should resolve to a valid Kokoro model");
    }

    @Test
    void testResolveUnknownOrNullVoiceFallback() {
        Model fallback1 = resolver.resolveVoice(null);
        assertNotNull(fallback1, "Null voice should fall back to default model");

        Model fallback2 = resolver.resolveVoice("unknown_voice_xyz");
        assertNotNull(fallback2, "Unknown voice should fall back to default model");
    }
}
