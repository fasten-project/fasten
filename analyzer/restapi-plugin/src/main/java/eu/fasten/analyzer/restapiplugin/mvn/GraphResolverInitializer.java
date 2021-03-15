package eu.fasten.analyzer.restapiplugin.mvn;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GraphResolverInitializer {

    public static void sendRequestToInitGraphResolver() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/__INTERNAL__/packages/0/directedgraph";
        try {
            restTemplate.getForObject(url, String.class);
        } catch (RestClientException ignored) {}
    }
}
