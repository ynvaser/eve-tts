package systems.bdev.evetts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import systems.bdev.evetts.dto.ChatMessageDto;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LogWatcherServiceTest {

    @BeforeEach
    void setUp() {
        LogWatcherService.getListenerIdCache().clear();
    }

    @Test
    void testParseTimestampAndListenerIdFromFilename() {
        String filename1 = "Degen Gambling_20260815_191406_2112000314.txt";
        assertEquals("2026.08.15 19:14:06", LogWatcherService.parseTimestampFromFilename(filename1));
        assertEquals("2112000314", LogWatcherService.parseListenerIdFromFilename(filename1));

        String filename2 = "Fleet_20260628_192443_2112665657.txt";
        assertEquals("2026.06.28 19:24:43", LogWatcherService.parseTimestampFromFilename(filename2));
        assertEquals("2112665657", LogWatcherService.parseListenerIdFromFilename(filename2));

        assertNull(LogWatcherService.parseTimestampFromFilename("invalid_filename.txt"));
        assertNull(LogWatcherService.parseListenerIdFromFilename("invalid_filename.txt"));
    }

    @Test
    void testListenerIdToCharacterNameCaching(@TempDir Path tempDir) throws Exception {
        LogWatcherService.getListenerIdCache().clear();

        Path logFile = tempDir.resolve("4CHIN_20260815_234720_2112000314.txt");
        String content = """
                ---------------------------------------------------------------
                  Channel Name:   4CHIN
                  Listener:       Davos Skyworth
                  SESSION START:  2026.08.15 23:47:20
                ---------------------------------------------------------------
                [ 2026.08.15 23:47:33 ] Davos Skyworth > test
                """;
        Files.writeString(logFile, content);

        File file = logFile.toFile();
        assertFalse(LogWatcherService.getListenerIdCache().containsKey("2112000314"));

        // First call populates cache from header
        String listenerName = LogWatcherService.getOrResolveListenerCharacterName(file);
        assertEquals("Davos Skyworth", listenerName);
        assertTrue(LogWatcherService.getListenerIdCache().containsKey("2112000314"));
        assertEquals("Davos Skyworth", LogWatcherService.getListenerIdCache().get("2112000314"));

        // Second call resolves instantly from cache
        String cachedName = LogWatcherService.getOrResolveListenerCharacterName(file);
        assertEquals("Davos Skyworth", cachedName);
    }

    @Test
    void testFilterRelevantLogFilesSkipsObsoleteFiles(@TempDir Path tempDir) throws Exception {
        Path fileOld = tempDir.resolve("Fleet_20260628_192443_1.txt");
        Path fileNew = tempDir.resolve("Fleet_20260815_192046_2.txt");

        Files.writeString(fileOld, "[ 2026.06.28 19:24:43 ] Pilot A > Old message\n");
        Files.writeString(fileNew, "[ 2026.08.15 19:20:46 ] Pilot B > Recent message\n");

        UserDataService userDataService = new UserDataService(tempDir.resolve("userdata.json").toString());
        TtsSpeechService ttsSpeechService = new TtsSpeechService();

        // Threshold timestamp: 2026.08.15 20:00:00
        LogMessageProcessService processService = new LogMessageProcessService(
                ttsSpeechService,
                userDataService,
                "2026.08.15 20:00:00"
        );

        LogWatcherService watcherService = new LogWatcherService(tempDir.toString(), processService, 50L);

        List<File> files = List.of(fileOld.toFile(), fileNew.toFile());
        List<File> relevant = watcherService.filterRelevantLogFiles(files, "Fleet");

        assertEquals(1, relevant.size());
        assertEquals("Fleet_20260815_192046_2.txt", relevant.get(0).getName());
    }

    @Test
    void testOpenBufferedReaderUTF16LEWithBOM(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("MyIntelChannel_20231024_153012.txt");
        String content = "[ 2023.10.24 15:30:12 ] TestPilot > Hostiles incoming\n";

        byte[] bom = new byte[]{(byte) 0xFF, (byte) 0xFE};
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] fullBytes = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, fullBytes, 0, bom.length);
        System.arraycopy(contentBytes, 0, fullBytes, bom.length, contentBytes.length);

        Files.write(logFile, fullBytes);

        try (BufferedReader reader = LogWatcherService.openBufferedReader(logFile.toFile())) {
            String line = reader.readLine();
            assertNotNull(line);
            assertTrue(line.contains("TestPilot > Hostiles incoming"));
        }
    }

    @Test
    void testLogWatcherServiceProcessesLinesInRealTime(@TempDir Path tempDir) throws Exception {
        Path logFile = tempDir.resolve("IntelChannel_20231024_120000_1.txt");
        Files.writeString(logFile, "[ 2023.10.24 12:00:00 ] InitialPilot > Line 1\n");

        List<String> processedMessages = new CopyOnWriteArrayList<>();
        UserDataService userDataService = new UserDataService(tempDir.resolve("userdata.json").toString());
        TtsSpeechService ttsSpeechService = new TtsSpeechService() {
            @Override
            public boolean logChatMessage(ChatMessageDto chatMessage) {
                processedMessages.add(chatMessage.getMessage());
                return super.logChatMessage(chatMessage);
            }
        };

        LogMessageProcessService processService = new LogMessageProcessService(ttsSpeechService, userDataService);
        LogWatcherService watcherService = new LogWatcherService(tempDir.toString(), processService, 50L);

        AtomicBoolean running = new AtomicBoolean(true);
        Thread thread = new Thread(() -> watcherService.runWatcher("IntelChannel", running));
        thread.start();

        // Give watcher time to read first line
        Thread.sleep(200);

        // Append line 2 to log file
        Files.writeString(logFile, "[ 2023.10.24 12:05:00 ] SecondPilot > Line 2\n", java.nio.file.StandardOpenOption.APPEND);
        Thread.sleep(300);

        running.set(false);
        thread.join(1000);

        assertFalse(processedMessages.isEmpty(), "Processed messages should not be empty");
        assertTrue(processedMessages.contains("Line 1") || processedMessages.contains("Line 2"));
    }
}
