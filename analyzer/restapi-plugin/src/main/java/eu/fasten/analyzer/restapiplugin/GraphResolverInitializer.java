package eu.fasten.analyzer.restapiplugin;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GraphResolverInitializer {

    public static void sendRequestToInitGraphResolver() {
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.getForObject(KnowledgeBaseConnector.initDepGraphRequest, String.class);
        } catch (RestClientException ignored) {}
    }
}
