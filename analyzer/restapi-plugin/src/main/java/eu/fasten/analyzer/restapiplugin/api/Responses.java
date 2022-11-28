package eu.fasten.analyzer.restapiplugin.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Responses {

    public static ResponseEntity<String> ok(String body) {
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    public static ResponseEntity<String> incorrectForge() {
        return new ResponseEntity<>("Incorrect forge", HttpStatus.BAD_REQUEST);
    }

    public static ResponseEntity<String> lazyIngestion() {
        return new ResponseEntity<>("Package version not found, but should be processed soon. Try again later.",
                HttpStatus.CREATED);
    }

    public static ResponseEntity<String> packageNotFound() {
        return new ResponseEntity<>("Package not found", HttpStatus.NOT_FOUND);
    }
    public static ResponseEntity<String> packageVersionNotFound() {
        return new ResponseEntity<>("Package version not found", HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<String> moduleNotFound() {
        return new ResponseEntity<>("Module not found", HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<String> binaryModuleNotFound() {
        return new ResponseEntity<>("Binary module not found", HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<String> failedToResolveDependents(String packageName, String packageVersion) {
        return new ResponseEntity<>("Failed to resolve dependents for revision " +
                packageName +
                ":" +
                packageVersion,
                HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<String> dataNotFound() {
        return new ResponseEntity<>("Could not find the requested data", HttpStatus.NOT_FOUND);
    }
}
