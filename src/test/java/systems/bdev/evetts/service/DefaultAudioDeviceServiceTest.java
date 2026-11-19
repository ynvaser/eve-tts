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
    void testIsDeviceAvailableNullOrEmptyReflectsDeviceAvailability() {
        boolean hasDevices = !audioService.getAvailableAudioDevices().isEmpty();
        assertEquals(hasDevices, audioService.isDeviceAvailable(null));
        assertEquals(hasDevices, audioService.isDeviceAvailable(""));
        assertEquals(hasDevices, audioService.isDeviceAvailable("   "));
    }

    @Test
    void testGetDefaultAudioDevice() {
        List<String> devices = audioService.getAvailableAudioDevices();
        if (!devices.isEmpty()) {
            assertEquals(devices.get(0), audioService.getDefaultAudioDevice());
        } else {
            assertNull(audioService.getDefaultAudioDevice());
        }
    }
}
