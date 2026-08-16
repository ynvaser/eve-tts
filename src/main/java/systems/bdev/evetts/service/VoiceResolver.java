package systems.bdev.evetts.service;

import org.pitest.voices.Model;

/**
 * Interface contract for resolving voice model aliases to Chorus Model instances.
 */
public interface VoiceResolver {

    /**
     * Resolves a voice model string identifier (alias) to a Chorus Model.
     *
     * @param voiceName The string alias configured for a character (e.g. "af_sarah", "lessac", "alba")
     * @return The resolved Model instance
     */
    Model resolveVoice(String voiceName);
}
