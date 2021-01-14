package eu.fasten.server.plugins.kafka;

import java.io.File;

public class LocalStorage {

    private final String instanceId;
    private final File storageFolder;

    public LocalStorage(String folder) {
        if (System.getenv("POD_INSTANCE_ID") != null) {
            instanceId = System.getenv("POD_INSTANCE_ID");
        } else {
            throw new IllegalArgumentException("Trying to initialize local storage but $POD_INSTANCE_ID is not in the environemnt variables.");
        }

        storageFolder = new File(folder);

        // Create folder if it doesn't exist yet.
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }


}
