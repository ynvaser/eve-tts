package systems.bdev.evetts.event;

import systems.bdev.evetts.model.ChatEvent;

/**
 * Listener interface for incoming chat events.
 */
@FunctionalInterface
public interface ChatEventListener {
    void onChatEvent(ChatEvent event);
}
