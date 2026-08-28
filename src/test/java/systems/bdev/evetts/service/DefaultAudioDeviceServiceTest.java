package systems.bdev.evetts.service;

import org.junit.jupiter.api.Test;
import systems.bdev.evetts.service.impl.DefaultAudioDeviceService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAudioDeviceServiceTest {

    private final DefaultAudioDeviceService audioService = new DefaultAudioDeviceService();

    @Test
    void testGetAvailableAudioDevicesDoesNotThrow() {
        List<String> devices = audioService.getAvailableAudioDevices();
        assertNotNull(devices);
    }

    @Test
    void testIsDeviceAvailableNullOrEmptyReturnsFalse() {
        assertFalse(audioService.isDeviceAvailable(null));
        assertFalse(audioService.isDeviceAvailable(""));
        assertFalse(audioService.isDeviceAvailable("   "));
    }
}
