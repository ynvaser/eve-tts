package systems.bdev.evetts.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.dto.ChatMessageDto;
import systems.bdev.evetts.service.LogMessageProcessService;
import systems.bdev.evetts.service.LogWatcherService;
import systems.bdev.evetts.service.TtsSpeechService;
import systems.bdev.evetts.service.UserDataService;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FourChinLogTest {

    private static final String TEST_DATA_DIR = "src/test/resources/data";

    @Test
    void testMultiClientListenerFilteringForDavosSkyworth(@TempDir Path tempDir) throws Exception {
        File dataDir = Paths.get(TEST_DATA_DIR).toFile();
        assertTrue(dataDir.exists() && dataDir.isDirectory(), "Test data directory src/test/resources/data must exist.");

        String startFromTimestamp = "2026.08.15 23:45:00";
        String userDataPath = tempDir.resolve("userdata_4chin.json").toString();
        UserDataService userDataService = new UserDataService(userDataPath);

        List<ChatMessageDto> capturedMessages = Collections.synchronizedList(new ArrayList<>());

        AppConfiguration config = new AppConfiguration();
        config.setEnableTts(false);
        config.getCharacterVoices().put("Davos Skyworth", "am_adam");

        TtsSpeechService ttsSpeechService = new TtsSpeechService(config, true) {
            @Override
            public boolean logChatMessage(ChatMessageDto chatMessage) {
                boolean result = super.logChatMessage(chatMessage);
                if (result) {
                    capturedMessages.add(chatMessage);
                }
                return result;
            }
        };

        LogMessageProcessService processService = new LogMessageProcessService(
                ttsSpeechService,
                userDataService,
                startFromTimestamp
        );

        LogWatcherService watcherService = new LogWatcherService(
                dataDir.getAbsolutePath(),
                processService,
                50L
        );

        AtomicBoolean running = new AtomicBoolean(true);
        Thread watcherThread = new Thread(() -> watcherService.runWatcher("4CHIN", running));
        watcherThread.start();

        Thread.sleep(600);

        running.set(false);
        watcherThread.join(1000);

        assertFalse(capturedMessages.isEmpty(), "Expected to process chat messages for Davos Skyworth from 4CHIN log file");

        boolean foundTest32 = false;
        for (ChatMessageDto chat : capturedMessages) {
            assertEquals("Davos Skyworth", chat.getSender(), "All captured messages must come from Davos Skyworth");
            assertEquals("4CHIN", chat.getChannelName());
            if (chat.getMessage().contains("test32")) {
                foundTest32 = true;
            }
        }

        assertTrue(foundTest32, "Should capture test32 message from Davos Skyworth's log file (4CHIN_20260815_234720_2112000314.txt)");
    }
}
