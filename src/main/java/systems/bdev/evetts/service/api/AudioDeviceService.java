package systems.bdev.evetts.service.api;

import java.util.List;

/**
 * Service contract for audio hardware discovery and validation.
 */
public interface AudioDeviceService {

    List<String> getAvailableAudioDevices();

    boolean isDeviceAvailable(String deviceName);
}
