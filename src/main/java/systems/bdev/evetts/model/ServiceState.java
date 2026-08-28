package systems.bdev.evetts.model;

/**
 * Lifecycle state of the EVE-TTS core application service.
 */
public enum ServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
