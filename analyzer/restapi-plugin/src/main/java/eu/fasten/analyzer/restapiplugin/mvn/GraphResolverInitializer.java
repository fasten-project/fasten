package eu.fasten.analyzer.restapiplugin;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GraphResolverInitializer {

    public static void sendRequestToInitGraphResolver() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/packages/package/0/resolve/dependents?transitive=false";
        try {
            restTemplate.getForObject(url, String.class);
        } catch (RestClientException ignored) {}
    }
}
