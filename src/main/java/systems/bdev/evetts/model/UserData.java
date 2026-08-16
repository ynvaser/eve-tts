package systems.bdev.evetts.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model holding persisted state such as last processed timestamps per channel.
 */
public class UserData {

    @JsonProperty("lastSubmittedChatChannelDates")
    private Map<String, String> lastSubmittedChatChannelDates = new ConcurrentHashMap<>();

    public UserData() {
    }

    public Map<String, String> getLastSubmittedChatChannelDates() {
        return lastSubmittedChatChannelDates;
    }

    public void setLastSubmittedChatChannelDates(Map<String, String> lastSubmittedChatChannelDates) {
        this.lastSubmittedChatChannelDates = lastSubmittedChatChannelDates != null
                ? new ConcurrentHashMap<>(lastSubmittedChatChannelDates)
                : new ConcurrentHashMap<>();
    }
}
