package systems.bdev.evetts.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioDevicePlayerTest {

    @Test
    void testValidateNonExistentDeviceThrowsException() {
        AudioDevicePlayer player = new AudioDevicePlayer();
        String missingDevice = "NonExistentDevice_XYZ_999";

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> player.validateAndSelectDevice(missingDevice),
                "Validating a missing audio device must throw IllegalStateException"
        );

        assertTrue(
                ex.getMessage().contains("Configured audio device") || ex.getMessage().contains(missingDevice),
                "Exception message should mention the missing device"
        );
    }

    @Test
    void testValidateBlankDeviceAutoSelectsFirst() {
        AudioDevicePlayer player = new AudioDevicePlayer();
        var available = AudioDevicePlayer.listAvailableOutputDevices();
        if (!available.isEmpty()) {
            var selectedNull = player.validateAndSelectDevice(null);
            assertNotNull(selectedNull, "Null device should auto-select first available mixer");

            var selectedBlank = player.validateAndSelectDevice("");
            assertNotNull(selectedBlank, "Blank device string should auto-select first available mixer");

            var selectedSpaces = player.validateAndSelectDevice("   ");
            assertNotNull(selectedSpaces, "Whitespace device string should auto-select first available mixer");
        }
    }
}
