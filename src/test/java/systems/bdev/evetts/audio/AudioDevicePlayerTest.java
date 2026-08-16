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
}
