package systems.bdev.evetts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventTest {

    @Test
    void testChatEventCreationAndGetters() {
        ChatEvent event = new ChatEvent(
                "2026.08.15 19:14:06",
                "Fleet",
                "Davos Skyworth",
                "am_michael",
                "Anchor up on FC!",
                ChatEventStatus.SPOKEN,
                "Success"
        );

        assertEquals("2026.08.15 19:14:06", event.getTimestamp());
        assertEquals("Fleet", event.getChannelName());
        assertEquals("Davos Skyworth", event.getSender());
        assertEquals("am_michael", event.getVoiceModel());
        assertEquals("Anchor up on FC!", event.getMessage());
        assertEquals(ChatEventStatus.SPOKEN, event.getStatus());
        assertEquals("Success", event.getDetails());
    }

    @Test
    void testNullVoiceModelDefaultsToDash() {
        ChatEvent event = new ChatEvent(
                "2026.08.15 19:14:06",
                "Fleet",
                "Unknown Pilot",
                null,
                "Hello",
                ChatEventStatus.FILTERED
        );

        assertEquals("-", event.getVoiceModel());
        assertEquals(ChatEventStatus.FILTERED, event.getStatus());
    }

    @Test
    void testEqualsAndHashCode() {
        ChatEvent event1 = new ChatEvent("12:00:00", "Fleet", "Pilot", "voice", "Msg", ChatEventStatus.SPOKEN);
        ChatEvent event2 = new ChatEvent("12:00:00", "Fleet", "Pilot", "voice", "Msg", ChatEventStatus.SPOKEN);
        ChatEvent event3 = new ChatEvent("12:00:00", "Fleet", "Pilot2", "voice", "Msg", ChatEventStatus.SPOKEN);

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
        assertNotEquals(event1, event3);
    }
}
