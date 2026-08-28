package systems.bdev.evetts.event;

import systems.bdev.evetts.model.ServiceState;

/**
 * Listener interface for service lifecycle state changes.
 */
@FunctionalInterface
public interface ServiceStateListener {
    void onStateChanged(ServiceState newState, String message);
}
