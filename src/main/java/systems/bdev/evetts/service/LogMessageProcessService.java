package systems.bdev.evetts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.dto.ChatMessageDto;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for parsing raw log lines into ChatMessageDto objects
 * and delegating valid messages to TtsSpeechService.
 */
public class LogMessageProcessService {

    private static final Logger logger = LoggerFactory.getLogger(LogMessageProcessService.class);

    // EVE Chat log line pattern: [ 2021.05.01 12:34:56 ] Sender Name > Message text
    private static final Pattern CHAT_LINE_PATTERN = Pattern.compile(
            "^\\s*\\[\\s*([0-9:.\\ ]+?)\\s*\\]\\s*(.*?)\\s*>\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private final TtsSpeechService ttsSpeechService;
    private final UserDataService userDataService;
    private final String defaultStartTimestamp;

    public LogMessageProcessService(TtsSpeechService ttsSpeechService, UserDataService userDataService) {
        this(ttsSpeechService, userDataService, null);
    }

    public LogMessageProcessService(TtsSpeechService ttsSpeechService, UserDataService userDataService, String defaultStartTimestamp) {
        this.ttsSpeechService = ttsSpeechService;
        this.userDataService = userDataService;
        this.defaultStartTimestamp = defaultStartTimestamp;
    }

    public String getThresholdTimestamp(String channelName) {
        Map<String, String> lastDates = userDataService.getUserData().getLastSubmittedChatChannelDates();
        String thresholdTimestamp = lastDates.get(channelName);
        if (thresholdTimestamp == null || thresholdTimestamp.trim().isEmpty()) {
            thresholdTimestamp = defaultStartTimestamp;
        }
        return thresholdTimestamp;
    }

    public void processLogMessage(String channelName, String messageLine) {
        if (messageLine == null || messageLine.trim().isEmpty()) {
            return;
        }

        ChatMessageDto dto = parseChatLine(messageLine, channelName);
        if (dto == null) {
            return;
        }

        if (dto.getSender() == null || dto.getSender().trim().isEmpty() || "EVE System".equalsIgnoreCase(dto.getSender().trim())) {
            return;
        }

        String thresholdTimestamp = getThresholdTimestamp(channelName);

        if (thresholdTimestamp != null && dto.getTimestamp() != null && dto.getTimestamp().compareTo(thresholdTimestamp) <= 0) {
            // Message is at or before start/last-processed timestamp - skip!
            return;
        }

        boolean success = ttsSpeechService.logChatMessage(dto);
        if (success) {
            logger.debug("[{}] Processed chat message from {}", channelName, dto.getSender());
            if (dto.getTimestamp() != null) {
                Map<String, String> lastDates = userDataService.getUserData().getLastSubmittedChatChannelDates();
                lastDates.put(channelName, dto.getTimestamp());
                userDataService.save();
            }
        }
    }

    public static ChatMessageDto parseChatLine(String line, String channelName) {
        if (line == null) {
            return null;
        }

        // Clean UTF-16LE / UTF-8 BOM characters (\uFEFF)
        String cleanedLine = line.replace("\uFEFF", "").trim();

        Matcher matcher = CHAT_LINE_PATTERN.matcher(cleanedLine);
        if (!matcher.matches()) {
            return null;
        }

        String timestamp = matcher.group(1).trim();
        String sender = matcher.group(2).trim();
        String message = matcher.group(3).trim();

        return new ChatMessageDto(channelName, message, sender, timestamp);
    }
}
