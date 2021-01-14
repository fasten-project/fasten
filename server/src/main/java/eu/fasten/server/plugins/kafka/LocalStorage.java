package eu.fasten.server.plugins.kafka;

import org.apache.commons.codec.digest.DigestUtils;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_1;
import java.io.File;
import java.io.IOException;

public class LocalStorage {

    private final String instanceId;
    private final File storageFolder;
    private final DigestUtils digestUtils = new DigestUtils(SHA_1);

    /**
     * Helper class to store a SHA-1 hash of a message in local storage of a plugin instance.
     * This can be used to detect if a plugin crashed while working on a certain input, and appropriate action can be taken.
     *
     * The folder name will be suffixed by the $POD_INSTANCE_ID env. variable. Ensure this is unique per plugin instance.
     * @param folder the folder to store in. A plugin instance should always have access to this folder (so preferable some sort of NFS mount).
     */
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

    /**
     * Verify if a message is already in the local storage.
     *
     * @param message the message to verify.
     * @return true if local storage, otherwise false.
     */
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

    /**
     * Remove a message from local storage.
     *
     * @param message the message to remove.
     * @return true if sucessfully deleted, otherwise false (for instance, when it doesn't exist).
     */
    public boolean delete(String message) {
        if (!exists(message)) {
            return false;
        }

        String hashedMessage = digestUtils.digestAsHex(message);
        File fileToRemove = new File(storageFolder.getPath() + File.separator + hashedMessage);

        return fileToRemove.delete();
    }

    /**
     * Stores a message in local storage.
     *
     * @param message the raw message to store. Will be hashed into SHA-1 format.
     * @return if successfully stored.
     * @throws IOException when file can't be created.
     */
    public boolean store(String message) throws IOException {
        if (exists(message)) {
            return false;
        }

        String hashedMessage = digestUtils.digestAsHex(message);
        File fileToCreate = new File(storageFolder.getPath() + File.separator + hashedMessage);

        return fileToCreate.createNewFile();
    }


}
