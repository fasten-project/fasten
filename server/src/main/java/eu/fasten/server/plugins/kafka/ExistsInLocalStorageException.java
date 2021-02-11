package eu.fasten.server.plugins.kafka;

public class ExistsInLocalStorageException extends Exception {

    public ExistsInLocalStorageException(String errorMessage) {
        super(errorMessage);
    }

}
