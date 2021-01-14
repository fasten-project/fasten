package eu.fasten.server.plugins.kafka;

import org.apache.commons.codec.digest.DigestUtils;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_1;
import java.io.File;
import java.io.IOException;

public class LocalStorage {

    private final String instanceId;
    private final File storageFolder;
    private final DigestUtils digestUtils = new DigestUtils(SHA_1);

    public LocalStorage(String folder) {
        if (System.getenv("POD_INSTANCE_ID") != null) {
            instanceId = System.getenv("POD_INSTANCE_ID");
        } else {
            throw new IllegalArgumentException("Trying to initialize local storage but $POD_INSTANCE_ID is not in the environemnt variables.");
        }

        storageFolder = new File(folder + File.separator + instanceId + File.separator);

        // Create folder if it doesn't exist yet.
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    public boolean exists(String message) {
        String hashedMessage = digestUtils.digestAsHex(message);
        String[] filesInFolder = storageFolder.list();

        for (String hash : filesInFolder) {
            if (hash.equals(hashedMessage)) {
                return true;
            }
        }
        return false;
    }

    public boolean delete(String message) {
        if (!exists(message)) {
            return false;
        }

        String hashedMessage = digestUtils.digestAsHex(message);
        File fileToRemove = new File(storageFolder.getPath() + File.separator + hashedMessage);

        return fileToRemove.delete();
    }

    public boolean store(String message) throws IOException {
        if (exists(message)) {
            return false;
        }

        String hashedMessage = digestUtils.digestAsHex(message);
        File fileToCreate = new File(storageFolder.getPath() + File.separator + hashedMessage);

        return fileToCreate.createNewFile();
    }


}
