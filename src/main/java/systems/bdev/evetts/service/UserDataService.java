package systems.bdev.evetts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.bdev.evetts.model.UserData;

import java.io.File;
import java.io.IOException;

/**
 * Service for loading and persisting UserData (last processed dates per channel).
 */
public class UserDataService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);
    private static final String DEFAULT_USER_DATA_FILE = "userdata.json";

    private final String filePath;
    private final ObjectMapper mapper;
    private UserData userData;

    public UserDataService() {
        this(DEFAULT_USER_DATA_FILE);
    }

    public UserDataService(String filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    public synchronized void load() {
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            try {
                this.userData = mapper.readValue(file, UserData.class);
                logger.info("Loaded user data state from {}", file.getAbsolutePath());
                return;
            } catch (IOException e) {
                logger.warn("Failed to read user data file {}, starting fresh: {}", filePath, e.getMessage());
            }
        }
        this.userData = new UserData();
    }

    public synchronized void save() {
        try {
            mapper.writeValue(new File(filePath), userData);
        } catch (IOException e) {
            logger.error("Could not save user data to {}: {}", filePath, e.getMessage());
        }
    }

    public UserData getUserData() {
        return userData;
    }
}
