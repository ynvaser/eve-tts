package systems.bdev.evetts.service.api;

import systems.bdev.evetts.config.AppConfiguration;

import java.io.IOException;

/**
 * Service contract for managing application configuration persistence and updates.
 */
public interface ConfigurationService {

    AppConfiguration getConfiguration();

    void updateConfiguration(AppConfiguration config);

    void saveConfiguration() throws IOException;

    void reloadConfiguration() throws IOException;

    AppConfiguration createDefaultConfiguration();
}
