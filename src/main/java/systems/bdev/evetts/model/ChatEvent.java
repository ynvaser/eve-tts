package systems.bdev.evetts.model;

import java.util.Objects;

/**
 * Domain model representing an audited EVE Online chat event.
 */
public class ChatEvent {

    private final String timestamp;
    private final String channelName;
    private final String sender;
    private final String voiceModel;
    private final String message;
    private final ChatEventStatus status;
    private final String details;

    public ChatEvent(String timestamp, String channelName, String sender, String voiceModel, String message, ChatEventStatus status) {
        this(timestamp, channelName, sender, voiceModel, message, status, null);
    }

    public ChatEvent(String timestamp, String channelName, String sender, String voiceModel, String message, ChatEventStatus status, String details) {
        this.timestamp = timestamp;
        this.channelName = channelName;
        this.sender = sender;
        this.voiceModel = voiceModel;
        this.message = message;
        this.status = status != null ? status : ChatEventStatus.IGNORED;
        this.details = details;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getSender() {
        return sender;
    }

    public String getVoiceModel() {
        return voiceModel != null ? voiceModel : "-";
    }

    public String getMessage() {
        return message;
    }

    public ChatEventStatus getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatEvent that)) return false;
        return Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(channelName, that.channelName) &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(voiceModel, that.voiceModel) &&
                Objects.equals(message, that.message) &&
                status == that.status &&
                Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, channelName, sender, voiceModel, message, status, details);
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s (%s) [%s]: %s",
                timestamp, channelName, sender, getVoiceModel(), status, message);
    }
}
