package eu.fasten.server.plugins.kafka;

import org.apache.commons.codec.digest.DigestUtils;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_1;
import java.io.File;
import java.io.IOException;
import java.util.List;

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
     * @param partition the partition this message belongs to.
     * @return true if local storage, otherwise false.
     */
    public boolean exists(String message, int partition) {
        String hashedMessage = getSHA1(message);
        String[] filesInFolder = getPartitionFolder(partition).list();

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
     * @param partition the partition this message belongs to.
     * @return true if sucessfully deleted, otherwise false (for instance, when it doesn't exist).
     */
    public boolean delete(String message, int partition) {
        if (!exists(message, partition)) {
            return false;
        }

        String hashedMessage = getSHA1(message);
        File fileToRemove = new File(getPartitionFolder(partition).getPath() + File.separator + hashedMessage);

        return fileToRemove.delete();
    }

    /**
     * Deletes a file by hash.
     *
     * @param hash the hash to remove from local storage.
     * @param partition the partition this message belongs to.
     * @return if successfully deleted.
     */
    private boolean deleteByHash(String hash, int partition) {
        File fileToRemove = new File(getPartitionFolder(partition).getPath() + File.separator + hash);
        return fileToRemove.delete();
    }

    /**
     * Stores a message in local storage.
     *
     * @param message the raw message to store. Will be hashed into SHA-1 format.
     * @param partition the partition this message belongs to.
     * @return if successfully stored.
     * @throws IOException when file can't be created.
     */
    public boolean store(String message, int partition) throws IOException {
        if (exists(message, partition)) {
            return false;
        }

        String hashedMessage = getSHA1(message);
        File fileToCreate = new File(getPartitionFolder(partition).getPath() + File.separator + hashedMessage);

        return fileToCreate.createNewFile();
    }

    /**
     * Remove all hashes/files from local storage.
     * @param partitions the partitions folders to remove from.
     */
    public void clear(List<Integer> partitions) {
        for (int partition : partitions) {
            for (String hash : getPartitionFolder(partition).list()) {
                deleteByHash(hash, partition);
            }
        }
    }

    /**
     * Get the folder of a certain partition based on the parent (storage) folder.
     * @param partition the partition number.
     * @return the partition folder (in a File instance).
     */
    public File getPartitionFolder(int partition) {
        File partitionFolder = new File(storageFolder.getPath() + File.separator + "partition-" + partition  + File.separator);

        if (!partitionFolder.exists()) {
            partitionFolder.mkdirs();
        }

        return partitionFolder;
    }

    /**
     * Hashes a message using SHA1.
     *
     * @param message the message to hash.
     * @return the hashed message.
     */
    public String getSHA1(String message) {
        return digestUtils.digestAsHex(message);
    }


}
