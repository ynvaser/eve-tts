package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.dto.ChatMessageDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TtsSpeechServiceTest {

    @Test
    void testLogChatMessageFiltersUnlistedSenders() {
        AppConfiguration config = new AppConfiguration();
        config.setEnableTts(false); // Silent mode for testing
        Map<String, String> characterVoices = new HashMap<>();
        characterVoices.put("Davos Skyworth", "am_adam");
        config.setCharacterVoices(characterVoices);

        TtsSpeechService ttsService = new TtsSpeechService(config, true);

        ChatMessageDto allowedMessage = new ChatMessageDto("Fleet", "Primary target is Battleship!", "Davos Skyworth", "2026.08.16 02:30:00");
        ChatMessageDto unlistedMessage = new ChatMessageDto("Fleet", "Hello world", "RandomPlayer", "2026.08.16 02:30:05");

        assertTrue(ttsService.logChatMessage(allowedMessage), "Allowed character message should be processed");
        assertFalse(ttsService.logChatMessage(unlistedMessage), "Unlisted character message should be skipped");
    }

    @Test
    void testExtractCleanMessageTextWithoutBoilerplate() {
        ChatMessageDto msg = new ChatMessageDto("Fleet", "Warp drive active.", "Davos Skyworth", "2026.08.16 02:30:00");
        assertEquals("Warp drive active.", msg.getMessage());
    }

    @Test
    void testSplitLongMessageIntoSafeChunks() {
        String longText = "Friends, Romans, countrymen, lend me your ears;I come to bury Caesar, not to praise him.The evil that men do lives after them;The good is oft interred with their bones;So let it be with Caesar. The noble BrutusHath told you Caesar was ambitious:If it were so, it was a grievous fault,And grievously hath Caesar answer’d it.";

        List<String> chunks = TtsSpeechService.splitIntoChunks(longText, 150);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty(), "Chunks list should not be empty");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 160, "Each chunk should be under max length limit: " + chunk.length() + " chars");
        }
    }

    @Test
    void testNormalizeUnicodeAndSpacingInChunks() {
        String input = "BrutusHath told you fault,And answer’d it.The rest—For Brutus";
        List<String> chunks = TtsSpeechService.splitIntoChunks(input, 200);

        assertEquals(1, chunks.size());
        String clean = chunks.get(0);
        assertTrue(clean.contains("Brutus Hath"), "Should separate fused CamelCase words");
        assertTrue(clean.contains("fault, And"), "Should add space after comma");
        assertTrue(clean.contains("answer'd"), "Should convert smart apostrophe");
        assertTrue(clean.contains("it. The"), "Should add space after period");
        assertTrue(clean.contains("rest - For"), "Should replace em dash with space-dash-space");
    }
}
