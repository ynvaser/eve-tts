package systems.bdev.evetts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object representing parsed EVE Online chat messages.
 */
public class ChatMessageDto {

    @JsonProperty("channelName")
    private String channelName;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("timestamp")
    private String timestamp;

    public ChatMessageDto() {
    }

    public ChatMessageDto(String channelName, String message, String sender, String timestamp) {
        this.channelName = channelName;
        this.message = message;
        this.sender = sender;
        this.timestamp = timestamp;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s > %s", channelName, timestamp, sender, message);
    }
}
