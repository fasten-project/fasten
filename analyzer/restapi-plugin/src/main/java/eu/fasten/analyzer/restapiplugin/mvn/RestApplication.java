package eu.fasten.analyzer.restapiplugin.mvn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestApplication {

    /**
     * Default page size (pagination is always enabled).
     */
    public static final String DEFAULT_PAGE_SIZE = "10";

    public static void main(String[] args) {
        SpringApplication.run(RestApplication.class, args);
    }
}
