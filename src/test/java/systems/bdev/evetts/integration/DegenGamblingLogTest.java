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

class DegenGamblingLogTest {

    private static final String TEST_DATA_DIR = "src/test/resources/data";

    @Test
    void testRealLogFileProcessingStartingFrom2026_08_15_20_00_00(@TempDir Path tempDir) throws Exception {
        File dataDir = Paths.get(TEST_DATA_DIR).toFile();
        assertTrue(dataDir.exists() && dataDir.isDirectory(), "Test data directory src/test/resources/data must exist.");

        String startFromTimestamp = "2026.08.15 20:00:00";
        String userDataPath = tempDir.resolve("userdata.json").toString();
        UserDataService userDataService = new UserDataService(userDataPath);

        List<ChatMessageDto> capturedMessages = Collections.synchronizedList(new ArrayList<>());

        TtsSpeechService consoleLogService = new TtsSpeechService() {
            @Override
            public boolean logChatMessage(ChatMessageDto chatMessage) {
                capturedMessages.add(chatMessage);
                return super.logChatMessage(chatMessage);
            }
        };

        LogMessageProcessService processService = new LogMessageProcessService(
                consoleLogService,
                userDataService,
                startFromTimestamp
        );

        LogWatcherService watcherService = new LogWatcherService(
                dataDir.getAbsolutePath(),
                processService,
                50L
        );

        AtomicBoolean running = new AtomicBoolean(true);
        Thread watcherThread = new Thread(() -> watcherService.runWatcher("Degen Gambling", running));
        watcherThread.start();

        // Allow watcher thread to process the log files
        Thread.sleep(500);

        running.set(false);
        watcherThread.join(1000);

        // Assert that chat messages after 2026.08.15 20:00:00 were processed
        assertFalse(capturedMessages.isEmpty(), "Expected to process chat messages after 2026.08.15 20:00:00");

        for (ChatMessageDto chat : capturedMessages) {
            assertTrue(
                    chat.getTimestamp().compareTo(startFromTimestamp) > 0,
                    "Chat timestamp " + chat.getTimestamp() + " should be strictly after " + startFromTimestamp
            );
            assertEquals("Degen Gambling", chat.getChannelName());
            assertNotNull(chat.getSender());
            assertNotNull(chat.getMessage());
        }

        // Verify persisted state in UserData
        String lastProcessed = userDataService.getUserData().getLastSubmittedChatChannelDates().get("Degen Gambling");
        assertNotNull(lastProcessed);
        assertTrue(lastProcessed.compareTo(startFromTimestamp) > 0);
    }

    @Test
    void testAllowedSendersFilterWithRealLogData(@TempDir Path tempDir) throws Exception {
        File dataDir = Paths.get(TEST_DATA_DIR).toFile();
        assertTrue(dataDir.exists() && dataDir.isDirectory());

        String startFromTimestamp = "2026.08.15 20:00:00";
        String userDataPath = tempDir.resolve("userdata_filter.json").toString();
        UserDataService userDataService = new UserDataService(userDataPath);

        List<ChatMessageDto> capturedMessages = Collections.synchronizedList(new ArrayList<>());

        AppConfiguration config = new AppConfiguration();
        config.setEnableTts(false);
        config.getCharacterVoices().put("Nerov Pat", "am_adam");

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
        Thread watcherThread = new Thread(() -> watcherService.runWatcher("Degen Gambling", running));
        watcherThread.start();

        Thread.sleep(500);

        running.set(false);
        watcherThread.join(1000);

        assertFalse(capturedMessages.isEmpty(), "Expected to process chat messages for Nerov Pat");
        for (ChatMessageDto chat : capturedMessages) {
            assertEquals("Nerov Pat", chat.getSender(), "All captured messages must come strictly from Nerov Pat");
        }
    }
}
