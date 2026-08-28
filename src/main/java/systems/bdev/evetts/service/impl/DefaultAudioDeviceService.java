package systems.bdev.evetts.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.audio.AudioDevicePlayer;
import systems.bdev.evetts.service.api.AudioDeviceService;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of AudioDeviceService querying system sound mixers.
 */
public class DefaultAudioDeviceService implements AudioDeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAudioDeviceService.class);

    @Override
    public List<String> getAvailableAudioDevices() {
        List<String> devices = new ArrayList<>();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info sourceLineInfo = new Line.Info(SourceDataLine.class);
                Line.Info clipInfo = new Line.Info(Clip.class);
                if (mixer.isLineSupported(sourceLineInfo) || mixer.isLineSupported(clipInfo)) {
                    String deviceName = info.getName();
                    if (!devices.contains(deviceName)) {
                        devices.add(deviceName);
                    }
                }
            } catch (Exception e) {
                logger.trace("Error querying mixer {}: {}", info.getName(), e.getMessage());
            }
        }
        return devices;
    }

    @Override
    public boolean isDeviceAvailable(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return false;
        }

        String searchKey = deviceName.trim().toLowerCase();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info sourceLineInfo = new Line.Info(SourceDataLine.class);
                Line.Info clipInfo = new Line.Info(Clip.class);
                if (mixer.isLineSupported(sourceLineInfo) || mixer.isLineSupported(clipInfo)) {
                    String fullName = (info.getName() + " " + info.getDescription()).toLowerCase();
                    if (fullName.contains(searchKey)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
