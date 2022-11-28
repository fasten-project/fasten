package eu.fasten.analyzer.restapiplugin.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Responses {
    public static ResponseEntity<String> getLazyIngestionResponse() {
        return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later.",
                HttpStatus.CREATED);
    }
}
