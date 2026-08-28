package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.model.ChatEvent;
import systems.bdev.evetts.model.ChatEventStatus;
import systems.bdev.evetts.model.ServiceState;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;
import systems.bdev.evetts.service.impl.DefaultAppService;
import systems.bdev.evetts.service.impl.DefaultAudioDeviceService;
import systems.bdev.evetts.service.impl.DefaultVoiceCatalogService;
import systems.bdev.evetts.service.impl.JsonConfigurationService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAppServiceTest {

    @Test
    void testAppServiceLifecycle(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        JsonConfigurationService configService = new JsonConfigurationService(configPath.toString());

        AppConfiguration config = configService.getConfiguration();
        config.setEveLogDirectory(tempDir.toString());
        config.setChannels(List.of("Fleet"));
        config.setEnableTts(false); // Disable TTS hardware output for unit test
        config.setCharacterVoices(Map.of("Davos", "am_michael"));
        configService.updateConfiguration(config);
        configService.saveConfiguration();

        UserDataService userDataService = new UserDataService(tempDir.resolve("userdata.json").toString());
        AudioDeviceService audioService = new DefaultAudioDeviceService();
        VoiceCatalogService voiceService = new DefaultVoiceCatalogService();

        DefaultAppService appService = new DefaultAppService(configService, audioService, voiceService, userDataService);

        assertEquals(ServiceState.STOPPED, appService.getState());
        assertFalse(appService.isRunning());

        List<ServiceState> stateHistory = new ArrayList<>();
        appService.addStateListener((newState, msg) -> stateHistory.add(newState));

        appService.start();
        assertEquals(ServiceState.RUNNING, appService.getState());
        assertTrue(appService.isRunning());
        assertTrue(stateHistory.contains(ServiceState.STARTING));
        assertTrue(stateHistory.contains(ServiceState.RUNNING));

        appService.stop();
        assertEquals(ServiceState.STOPPED, appService.getState());
        assertFalse(appService.isRunning());
        assertTrue(stateHistory.contains(ServiceState.STOPPING));
        assertTrue(stateHistory.contains(ServiceState.STOPPED));
    }

    @Test
    void testChatEventListeners(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("config.json");
        JsonConfigurationService configService = new JsonConfigurationService(configPath.toString());
        AudioDeviceService audioService = new DefaultAudioDeviceService();
        VoiceCatalogService voiceService = new DefaultVoiceCatalogService();
        UserDataService userDataService = new UserDataService(tempDir.resolve("userdata.json").toString());

        DefaultAppService appService = new DefaultAppService(configService, audioService, voiceService, userDataService);

        List<ChatEvent> received = new ArrayList<>();
        systems.bdev.evetts.event.ChatEventListener listener = received::add;

        appService.addChatEventListener(listener);
        appService.removeChatEventListener(listener);
        // Verify listener management doesn't throw
    }
}
