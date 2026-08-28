package systems.bdev.evetts.service.impl;

import org.pitest.voices.Chorus;
import org.pitest.voices.ChorusConfig;
import org.pitest.voices.Voice;
import org.pitest.voices.audio.Audio;
import org.pitest.voices.g2p.core.dictionary.Dictionaries;
import org.pitest.voices.openvoice.OpenVoiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.audio.AudioDevicePlayer;
import systems.bdev.evetts.config.AppConfiguration;
import systems.bdev.evetts.event.ChatEventListener;
import systems.bdev.evetts.event.ServiceStateListener;
import systems.bdev.evetts.model.ChatEvent;
import systems.bdev.evetts.model.ServiceState;
import systems.bdev.evetts.service.LogMessageProcessService;
import systems.bdev.evetts.service.LogWatcherServiceRunner;
import systems.bdev.evetts.service.TtsSpeechService;
import systems.bdev.evetts.service.UserDataService;
import systems.bdev.evetts.service.VoiceResolver;
import systems.bdev.evetts.service.api.AppService;
import systems.bdev.evetts.service.api.AudioDeviceService;
import systems.bdev.evetts.service.api.ConfigurationService;
import systems.bdev.evetts.service.api.VoiceCatalogService;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise-grade implementation of AppService managing lifecycle, log tailing,
 * TTS synthesis, event distribution, and background voice auditioning.
 */
public class DefaultAppService implements AppService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAppService.class);

    private final ConfigurationService configurationService;
    private final AudioDeviceService audioDeviceService;
    private final VoiceCatalogService voiceCatalogService;
    private final UserDataService userDataService;

    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.STOPPED);
    private final List<ChatEventListener> chatEventListeners = new CopyOnWriteArrayList<>();
    private final List<ServiceStateListener> stateListeners = new CopyOnWriteArrayList<>();

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EVE-TTS-AsyncWorker");
        t.setDaemon(true);
        return t;
    });

    private TtsSpeechService ttsSpeechService;
    private LogWatcherServiceRunner watcherRunner;

    public DefaultAppService(
            ConfigurationService configurationService,
            AudioDeviceService audioDeviceService,
            VoiceCatalogService voiceCatalogService
    ) {
        this(configurationService, audioDeviceService, voiceCatalogService, new UserDataService());
    }

    public DefaultAppService(
            ConfigurationService configurationService,
            AudioDeviceService audioDeviceService,
            VoiceCatalogService voiceCatalogService,
            UserDataService userDataService
    ) {
        this.configurationService = Objects.requireNonNull(configurationService, "ConfigurationService is required");
        this.audioDeviceService = Objects.requireNonNull(audioDeviceService, "AudioDeviceService is required");
        this.voiceCatalogService = Objects.requireNonNull(voiceCatalogService, "VoiceCatalogService is required");
        this.userDataService = Objects.requireNonNull(userDataService, "UserDataService is required");
    }

    @Override
    public ServiceState getState() {
        return state.get();
    }

    @Override
    public boolean isRunning() {
        return state.get() == ServiceState.RUNNING;
    }

    @Override
    public synchronized void start() throws Exception {
        if (state.get() == ServiceState.RUNNING || state.get() == ServiceState.STARTING) {
            logger.warn("AppService start requested, but already in state: {}", state.get());
            return;
        }

        updateState(ServiceState.STARTING, "Initializing EVE-TTS services...");

        try {
            AppConfiguration config = configurationService.getConfiguration();
            if (config == null) {
                throw new IllegalStateException("Application configuration is null.");
            }

            String logDir = config.getEveLogDirectory();
            File dir = new File(logDir);
            if (!dir.exists() || !dir.isDirectory()) {
                logger.warn("Configured EVE Log Directory '{}' does not currently exist. Service will watch and wait for it.", logDir);
            }

            if (config.isEnableTts()) {
                String targetDevice = config.getAudioDevice();
                if (targetDevice != null && !targetDevice.trim().isEmpty() && !audioDeviceService.isDeviceAvailable(targetDevice)) {
                    logger.warn("Configured audio device '{}' may not be active.", targetDevice);
                }
            }

            this.ttsSpeechService = new TtsSpeechService(config);
            this.ttsSpeechService.addChatEventListener(this::notifyChatEvent);

            LogMessageProcessService processService = new LogMessageProcessService(
                    ttsSpeechService,
                    userDataService,
                    config.getStartFromTimestamp()
            );

            this.watcherRunner = new LogWatcherServiceRunner(config, processService);
            this.watcherRunner.start();

            updateState(ServiceState.RUNNING, "EVE-TTS service is active and monitoring log files.");
            logger.info("DefaultAppService successfully started in state RUNNING.");
        } catch (Exception e) {
            logger.error("Failed to start DefaultAppService: {}", e.getMessage(), e);
            updateState(ServiceState.ERROR, "Startup failed: " + e.getMessage());
            cleanupResources();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (state.get() == ServiceState.STOPPED || state.get() == ServiceState.STOPPING) {
            return;
        }

        updateState(ServiceState.STOPPING, "Stopping EVE-TTS services...");
        cleanupResources();
        updateState(ServiceState.STOPPED, "EVE-TTS service stopped.");
        logger.info("DefaultAppService stopped cleanly.");
    }

    @Override
    public synchronized void restart() throws Exception {
        logger.info("Restarting DefaultAppService...");
        stop();
        start();
    }

    private void cleanupResources() {
        if (watcherRunner != null) {
            try {
                watcherRunner.stop();
            } catch (Exception e) {
                logger.warn("Error stopping watcher runner: {}", e.getMessage());
            }
            watcherRunner = null;
        }

        if (ttsSpeechService != null) {
            try {
                ttsSpeechService.close();
            } catch (Exception e) {
                logger.warn("Error closing TTS speech service: {}", e.getMessage());
            }
            ttsSpeechService = null;
        }

        if (userDataService != null) {
            try {
                userDataService.save();
            } catch (Exception e) {
                logger.warn("Error saving user data: {}", e.getMessage());
            }
        }
    }

    @Override
    public CompletableFuture<Void> testVoice(String engine, String voiceName, String audioDevice, String sampleText) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Auditioning voice '{}' [Engine: {}] on device '{}': \"{}\"", voiceName, engine, audioDevice, sampleText);
            VoiceResolver resolver = TtsSpeechService.createVoiceResolver(engine);
            Path voicesCacheDir = Paths.get("voices").toAbsolutePath().normalize();

            ChorusConfig chorusConfig = ChorusConfig.chorusConfig(Dictionaries.empty())
                    .withBase(voicesCacheDir)
                    .withModel(new OpenVoiceSupplier());

            try (Chorus auditionChorus = new Chorus(chorusConfig)) {
                Voice voice = auditionChorus.voice(resolver.resolveVoice(voiceName));
                String textToSay = (sampleText != null && !sampleText.trim().isEmpty())
                        ? sampleText.trim()
                        : "Fleet warp drive active. All pilots align to stargate.";

                Audio audio = voice.say(textToSay);
                byte[] wavBytes = audio.asBytes();

                AudioDevicePlayer player = new AudioDevicePlayer();
                player.validateAndSelectDevice(audioDevice);
                player.play(wavBytes);
                logger.info("Voice audition completed successfully.");
            } catch (Exception e) {
                logger.error("Failed voice audition for '{}': {}", voiceName, e.getMessage(), e);
                throw new RuntimeException("Voice audition failed: " + e.getMessage(), e);
            }
        }, asyncExecutor);
    }

    @Override
    public void addChatEventListener(ChatEventListener listener) {
        if (listener != null && !chatEventListeners.contains(listener)) {
            chatEventListeners.add(listener);
        }
    }

    @Override
    public void removeChatEventListener(ChatEventListener listener) {
        if (listener != null) {
            chatEventListeners.remove(listener);
        }
    }

    private void notifyChatEvent(ChatEvent event) {
        for (ChatEventListener listener : chatEventListeners) {
            try {
                listener.onChatEvent(event);
            } catch (Exception e) {
                logger.error("Error distributing chat event to listener: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void addStateListener(ServiceStateListener listener) {
        if (listener != null && !stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    @Override
    public void removeStateListener(ServiceStateListener listener) {
        if (listener != null) {
            stateListeners.remove(listener);
        }
    }

    private void updateState(ServiceState newState, String message) {
        this.state.set(newState);
        for (ServiceStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(newState, message);
            } catch (Exception e) {
                logger.error("Error notifying state listener: {}", e.getMessage(), e);
            }
        }
    }
}
