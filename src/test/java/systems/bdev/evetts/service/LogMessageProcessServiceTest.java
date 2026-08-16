package systems.bdev.evetts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import systems.bdev.evetts.dto.ChatMessageDto;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogMessageProcessServiceTest {

    private TtsSpeechService ttsSpeechService;
    private UserDataService userDataService;
    private LogMessageProcessService processService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        String testUserDataPath = tempDir.resolve("userdata.json").toString();
        userDataService = new UserDataService(testUserDataPath);
        ttsSpeechService = new TtsSpeechService();
        processService = new LogMessageProcessService(ttsSpeechService, userDataService);
    }

    @Test
    void testParseChatLineSuccess() {
        String rawLine = "[ 2023.10.24 15:30:12 ] Pilot Alpha > HyperNet offer: Barghest 4/8";
        ChatMessageDto dto = LogMessageProcessService.parseChatLine(rawLine, "Degen Gambling");

        assertNotNull(dto);
        assertEquals("Degen Gambling", dto.getChannelName());
        assertEquals("2023.10.24 15:30:12", dto.getTimestamp());
        assertEquals("Pilot Alpha", dto.getSender());
        assertEquals("HyperNet offer: Barghest 4/8", dto.getMessage());
    }

    @Test
    void testParseChatLineInvalidReturnsNull() {
        String headerLine = "---------------------------------------------------------------";
        assertNull(LogMessageProcessService.parseChatLine(headerLine, "TestChannel"));

        String metadataLine = "  Channel Name:   TestChannel";
        assertNull(LogMessageProcessService.parseChatLine(metadataLine, "TestChannel"));
    }

    @Test
    void testProcessLogMessageFiltersEveSystem() {
        String eveSystemLine = "[ 2023.10.24 15:30:12 ] EVE System > Channel listener changed to Pilot Alpha";
        processService.processLogMessage("TestChannel", eveSystemLine);

        // State should remain empty because EVE System is ignored
        assertNull(userDataService.getUserData().getLastSubmittedChatChannelDates().get("TestChannel"));
    }

    @Test
    void testProcessLogMessageUpdatesLastProcessedTimestamp() {
        String line = "[ 2023.10.24 15:30:12 ] Pilot Alpha > Hello chat";
        processService.processLogMessage("TestChannel", line);

        assertEquals("2023.10.24 15:30:12", userDataService.getUserData().getLastSubmittedChatChannelDates().get("TestChannel"));
    }

    @Test
    void testProcessLogMessageIgnoresOlderDuplicateMessages() {
        String line1 = "[ 2023.10.24 15:30:12 ] Pilot Alpha > Hello chat";
        String line2Older = "[ 2023.10.24 15:20:00 ] Pilot Beta > Old message";

        processService.processLogMessage("TestChannel", line1);
        assertEquals("2023.10.24 15:30:12", userDataService.getUserData().getLastSubmittedChatChannelDates().get("TestChannel"));

        // Process older line - timestamp state should NOT regress
        processService.processLogMessage("TestChannel", line2Older);
        assertEquals("2023.10.24 15:30:12", userDataService.getUserData().getLastSubmittedChatChannelDates().get("TestChannel"));
    }
}
