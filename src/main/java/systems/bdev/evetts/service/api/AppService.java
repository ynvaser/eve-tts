package systems.bdev.evetts.service.api;

import systems.bdev.evetts.event.ChatEventListener;
import systems.bdev.evetts.event.ServiceStateListener;
import systems.bdev.evetts.model.ServiceState;

import java.util.concurrent.CompletableFuture;

/**
 * High-level domain contract for the EVE-TTS application lifecycle and voice auditioning.
 */
public interface AppService {

    ServiceState getState();

    boolean isRunning();

    void start() throws Exception;

    void stop();

    void restart() throws Exception;

    CompletableFuture<Void> testVoice(String engine, String voiceName, String audioDevice, String sampleText);

    void addChatEventListener(ChatEventListener listener);

    void removeChatEventListener(ChatEventListener listener);

    void addStateListener(ServiceStateListener listener);

    void removeStateListener(ServiceStateListener listener);
}
