package systems.bdev.evetts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import systems.bdev.evetts.dto.ChatMessageDto;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service that logs chat messages to console and synthesizes/plays real-time Text-to-Speech (TTS)
 * using Kokoro ONNX or Piper voice models via the hcoles/voices library.
 */
public class TtsSpeechService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TtsSpeechService.class);
    private static final Logger chatLogger = LoggerFactory.getLogger("CHAT");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppConfiguration config;
    private final VoiceResolver voiceResolver;
    private final AudioDevicePlayer audioPlayer;
    private final ExecutorService ttsExecutor;
    private final Map<String, Voice> voiceCache = new ConcurrentHashMap<>();

    private Chorus chorus;
    private boolean initialized = false;

    public TtsSpeechService() {
        this(new AppConfiguration(), null, true);
    }

    public TtsSpeechService(AppConfiguration config) {
        this(config, null, config != null ? !config.isEnableTts() : true);
    }

    public TtsSpeechService(AppConfiguration config, boolean silentMode) {
        this(config, null, silentMode);
    }

    public TtsSpeechService(AppConfiguration config, VoiceResolver voiceResolver, boolean silentMode) {
        this.config = config != null ? config : new AppConfiguration();
        this.voiceResolver = voiceResolver != null ? voiceResolver : createVoiceResolver(this.config.getTtsEngine());
        this.audioPlayer = new AudioDevicePlayer(silentMode);
        this.ttsExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EVE-TTS-AudioQueue");
            t.setDaemon(true);
            return t;
        });

        if (this.config.isEnableTts() && !silentMode) {
            initTtsResources();
        }
    }

    public static VoiceResolver createVoiceResolver(String engine) {
        if ("piper".equalsIgnoreCase(engine)) {
            logger.info("Initializing Piper Voice Resolver...");
            return new PiperVoiceResolver();
        }
        logger.info("Initializing Kokoro Voice Resolver...");
        return new KokoroVoiceResolver();
    }

    private synchronized void initTtsResources() {
        if (initialized) {
            return;
        }

        if (config.getCharacterVoices() == null || config.getCharacterVoices().isEmpty()) {
            throw new IllegalStateException("characterVoices configuration is required and cannot be empty.");
        }

        try {
            Path voicesCacheDir = Paths.get("voices").toAbsolutePath().normalize();
            ChorusConfig chorusConfig;
            if (config.isUseGpu()) {
                logger.info("Initializing {} TTS engine with GPU (CUDA) acceleration enabled (Model cache dir: {})...", config.getTtsEngine(), voicesCacheDir);
                chorusConfig = ChorusConfig.gpuChorusConfig(Dictionaries.empty())
                        .withBase(voicesCacheDir)
                        .withModel(new OpenVoiceSupplier());
            } else {
                logger.info("Initializing {} TTS engine in CPU mode (Model cache dir: {})...", config.getTtsEngine(), voicesCacheDir);
                chorusConfig = ChorusConfig.chorusConfig(Dictionaries.empty())
                        .withBase(voicesCacheDir)
                        .withModel(new OpenVoiceSupplier());
            }
            this.chorus = new Chorus(chorusConfig);

            // Validate and select the audio output device
            audioPlayer.validateAndSelectDevice(config.getAudioDevice());

            // Eagerly pre-load all configured character voice models at startup
            eagerlyFetchConfiguredVoices();

            initialized = true;
            logger.info("TTS Engine ({}) successfully initialized with {} pre-loaded voice(s).", config.getTtsEngine(), voiceCache.size());
        } catch (Exception e) {
            logger.error("Failed to initialize TTS engine ({}) or Audio Device: {}", config.getTtsEngine(), e.getMessage(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException("Failed to initialize TTS engine: " + e.getMessage(), e);
        }
    }

    private void eagerlyFetchConfiguredVoices() {
        Map<String, String> characterVoices = config.getCharacterVoices();
        if (characterVoices == null || characterVoices.isEmpty()) {
            return;
        }

        Set<String> uniqueVoiceNames = new HashSet<>(characterVoices.values());
        for (String voiceName : uniqueVoiceNames) {
            if (voiceName == null || voiceName.trim().isEmpty()) {
                continue;
            }
            String cleanName = voiceName.trim();
            if (!voiceCache.containsKey(cleanName)) {
                logger.info("Eagerly fetching and pre-loading voice model '{}' at startup...", cleanName);
                try {
                    Voice voice = chorus.voice(voiceResolver.resolveVoice(cleanName));
                    voiceCache.put(cleanName, voice);
                } catch (Exception e) {
                    logger.warn("Could not pre-load voice model '{}': {}", cleanName, e.getMessage());
                }
            }
        }
    }

    public boolean logChatMessage(ChatMessageDto chatMessage) {
        if (chatMessage == null) {
            return false;
        }

        String sender = chatMessage.getSender();
        if (sender == null || sender.trim().isEmpty()) {
            return false;
        }

        String cleanSender = sender.trim();

        // Character Filtering: If characterVoices is populated, only process messages from explicitly defined characters
        Map<String, String> characterVoices = config.getCharacterVoices();
        boolean hasVoices = characterVoices != null && !characterVoices.isEmpty();
        String voiceModelName = null;

        if (hasVoices) {
            for (Map.Entry<String, String> entry : characterVoices.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cleanSender)) {
                    voiceModelName = entry.getValue();
                    break;
                }
            }

            if (voiceModelName == null) {
                logger.trace("Sender '{}' is not explicitly defined in characterVoices. Skipping message.", cleanSender);
                return false;
            }
        }

        // Formatted console logging
        String formattedOutput = String.format(
                "💬 [CHAT] Channel: %s | Time: %s | Sender: %s | Message: %s",
                chatMessage.getChannelName(),
                chatMessage.getTimestamp(),
                chatMessage.getSender(),
                chatMessage.getMessage()
        );

        chatLogger.info(formattedOutput);

        try {
            String jsonPayload = objectMapper.writeValueAsString(chatMessage);
            logger.debug("Chat JSON Payload: {}", jsonPayload);
        } catch (JsonProcessingException e) {
            logger.trace("Error serializing chat message to JSON: {}", e.getMessage());
        }

        // Schedule TTS synthesis & playback if enabled and voice model is mapped
        if (config.isEnableTts() && voiceModelName != null && chatMessage.getMessage() != null && !chatMessage.getMessage().trim().isEmpty()) {
            String textToSpeak = chatMessage.getMessage().trim();
            final String selectedVoice = voiceModelName;
            ttsExecutor.submit(() -> speakMessage(selectedVoice, textToSpeak));
        }

        return true;
    }

    public static List<String> splitIntoChunks(String text, int maxChunkLen) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 1. Normalize Unicode smart quotes, dashes, and special symbols
        String normalized = text
                .replace("’", "'")
                .replace("‘", "'")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("—", " - ")
                .replace("–", " - ");

        // 2. Separate fused words where lowercase is immediately followed by uppercase (e.g. "BrutusHath" -> "Brutus Hath")
        normalized = normalized.replaceAll("([a-z])([A-Z])", "$1 $2");

        // 3. Normalize punctuation spacing including commas (e.g. "fault,And" -> "fault, And", "him.The" -> "him. The")
        normalized = normalized.replaceAll("([.,;:!?])([A-Za-z])", "$1 $2");

        // 4. Split by major punctuation / sentence / newline boundaries
        String[] sentences = normalized.split("(?<=[.;:!?\\n])\\s+");

        StringBuilder current = new StringBuilder();
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (current.length() + trimmed.length() + 1 <= maxChunkLen) {
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(trimmed);
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }

                if (trimmed.length() > maxChunkLen) {
                    String[] words = trimmed.split("\\s+");
                    for (String w : words) {
                        if (current.length() + w.length() + 1 <= maxChunkLen) {
                            if (current.length() > 0) {
                                current.append(" ");
                            }
                            current.append(w);
                        } else {
                            if (current.length() > 0) {
                                chunks.add(current.toString());
                                current.setLength(0);
                            }
                            current.append(w);
                        }
                    }
                } else {
                    current.append(trimmed);
                }
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    private void speakMessage(String voiceModelName, String text) {
        if (audioPlayer.isSilentMode()) {
            return;
        }

        try {
            if (chorus == null) {
                initTtsResources();
            }

            Voice voice = voiceCache.computeIfAbsent(voiceModelName, name -> {
                logger.info("Loading Voice Model '{}' into Chorus session...", name);
                return chorus.voice(voiceResolver.resolveVoice(name));
            });

            List<String> chunks = splitIntoChunks(text, 180);
            for (String chunk : chunks) {
                if (chunk.trim().isEmpty()) {
                    continue;
                }
                try {
                    logger.debug("Synthesizing speech chunk for voice '{}': \"{}\"", voiceModelName, chunk);
                    logger.info("Saying: {}", chunk);
                    Audio audio = voice.say(chunk);
                    byte[] wavBytes = audio.asBytes();
                    audioPlayer.play(wavBytes);
                } catch (Exception chunkEx) {
                    logger.warn("Could not synthesize chunk \"{}\" with voice '{}': {}", chunk, voiceModelName, chunkEx.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error during TTS speech synthesis or playback: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        logger.info("Shutting down TtsSpeechService and TTS resources...");
        ttsExecutor.shutdown();
        try {
            if (!ttsExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ttsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ttsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (chorus != null) {
            try {
                chorus.close();
            } catch (Exception e) {
                logger.warn("Error closing Chorus TTS instance: {}", e.getMessage());
            }
        }
    }
}
