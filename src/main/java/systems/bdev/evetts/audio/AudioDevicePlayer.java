package systems.bdev.evetts.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles audio playback targeted to a specific hardware or virtual audio device (Mixer).
 */
public class AudioDevicePlayer {

    private static final Logger logger = LoggerFactory.getLogger(AudioDevicePlayer.class);

    private Mixer.Info selectedMixerInfo;
    private boolean silentMode = false;

    public AudioDevicePlayer() {
    }

    public AudioDevicePlayer(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public Mixer.Info getSelectedMixerInfo() {
        return selectedMixerInfo;
    }

    public static List<String> listAvailableOutputDevices() {
        List<String> devices = new ArrayList<>();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info sourceLineInfo = new Line.Info(SourceDataLine.class);
                Line.Info clipInfo = new Line.Info(Clip.class);
                if (mixer.isLineSupported(sourceLineInfo) || mixer.isLineSupported(clipInfo)) {
                    String deviceEntry = String.format("%s - %s", info.getName(), info.getDescription());
                    devices.add(deviceEntry);
                }
            } catch (Exception e) {
                // Ignore unreadable mixers
            }
        }
        return devices;
    }

    public Mixer.Info validateAndSelectDevice(String targetDeviceName) {
        if (silentMode) {
            logger.info("AudioDevicePlayer running in silent mode (playback disabled).");
            return null;
        }

        List<String> availableDevices = listAvailableOutputDevices();
        logger.info("Scanning for available system audio output devices...");
        for (String dev : availableDevices) {
            logger.info("  - Output Device: {}", dev);
        }

        if (targetDeviceName == null || targetDeviceName.trim().isEmpty()) {
            String errMsg = "Configured audio device name is blank or null! Application stopping.\nAvailable Devices:\n" +
                    String.join("\n", availableDevices);
            logger.error(errMsg);
            throw new IllegalStateException(errMsg);
        }

        String searchKey = targetDeviceName.trim().toLowerCase();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info sourceLineInfo = new Line.Info(SourceDataLine.class);
                Line.Info clipInfo = new Line.Info(Clip.class);

                if (mixer.isLineSupported(sourceLineInfo) || mixer.isLineSupported(clipInfo)) {
                    String fullName = (info.getName() + " " + info.getDescription()).toLowerCase();
                    if (fullName.contains(searchKey)) {
                        this.selectedMixerInfo = info;
                        logger.info("Successfully selected target audio device: '{}' [{}]", info.getName(), info.getDescription());
                        return info;
                    }
                }
            } catch (Exception e) {
                // Ignore errors checking individual mixers
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Configured audio device '").append(targetDeviceName).append("' not found!\n");
        sb.append("Available Audio Output Devices on system:\n");
        for (String dev : availableDevices) {
            sb.append("  - ").append(dev).append("\n");
        }

        String errorReport = sb.toString();
        logger.error(errorReport);
        throw new IllegalStateException(errorReport);
    }

    public void play(byte[] wavBytes) {
        if (silentMode || wavBytes == null || wavBytes.length == 0) {
            return;
        }

        if (selectedMixerInfo == null) {
            logger.warn("No target audio device selected; skipping playback.");
            return;
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
            AudioFormat format = ais.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (mixer.isLineSupported(info)) {
                SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                line.open(format);
                line.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = ais.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }
                line.drain();
                line.stop();
                line.close();
            } else {
                // Fallback to Clip if SourceDataLine direct format is not supported
                DataLine.Info clipInfo = new DataLine.Info(Clip.class, format);
                if (mixer.isLineSupported(clipInfo)) {
                    Clip clip = (Clip) mixer.getLine(clipInfo);
                    clip.open(ais);
                    clip.start();
                    Thread.sleep((clip.getMicrosecondLength() / 1000) + 100);
                    clip.close();
                } else {
                    logger.error("Mixer '{}' does not support line format: {}", selectedMixerInfo.getName(), format);
                }
            }
        } catch (Exception e) {
            logger.error("Error during audio playback on device '{}': {}", selectedMixerInfo.getName(), e.getMessage(), e);
        }
    }
}
