package eu.fasten.analyzer.restapiplugin;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GraphResolversInitializer {

    public static void sendRequestToInitGraphMavenResolver() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/__INTERNAL__/packages/0/directedgraph";
        try {
            restTemplate.getForObject(url, String.class);
        } catch (RestClientException ignored) {}
    }

    public static void sendRequestToInitGraphPyPiResolver() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/pypi/packages/shap/0.34.0/resolve/dependents?transitive=false";
        try {
            restTemplate.getForObject(url, String.class);
        } catch (RestClientException ignored) {}
    }

    public static void sendRequestToInitGraphDebianResolver() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/debian/packages/amanda-common/1:3.5.1-2/resolve/dependents?transitive=false";
        try {
            restTemplate.getForObject(url, String.class);
        } catch (RestClientException ignored) {}
    }
}
