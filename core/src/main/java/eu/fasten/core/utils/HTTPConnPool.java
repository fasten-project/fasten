package eu.fasten.core.utils;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.io.InputStream;

/**
 * A utility class to reuse a HTTP connection for sending many requests in a short time
 */
public class HTTPConnPool {

    private static final PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();

    public HTTPConnPool() {
        poolingConnManager.setMaxTotal(100);
        poolingConnManager.setDefaultMaxPerRoute(20);
    }

    public InputStream sendHTTPRequest(String url) throws IOException {

        CloseableHttpClient client = HttpClients.custom().setConnectionManager(poolingConnManager).build();
        return client.execute(new HttpGet(url)).getEntity().getContent();
    }

    public void cleanHTTPConnPool() {
        poolingConnManager.close();
    }

}
