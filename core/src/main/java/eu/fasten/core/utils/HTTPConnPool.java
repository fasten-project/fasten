package eu.fasten.core.utils;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * A utility class to reuse a HTTP connection for sending many requests in a short time
 */
public class HTTPConnPool {

    private static final Logger logger = LoggerFactory.getLogger(HTTPConnPool.class.getName());
    private static final RequestConfig reqConfig = RequestConfig.custom().setConnectionRequestTimeout(60 * 1000).build();
    private static CloseableHttpClient client;
    private static CloseableHttpResponse response;

    private HTTPConnPool() {
    }

    public static InputStream sendHTTPRequest(String url) throws IOException, HttpException {

        if (client == null) {
            PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();
            poolingConnManager.setMaxTotal(1000);
            poolingConnManager.setDefaultMaxPerRoute(500);
            client = HttpClients.custom().setConnectionManager(poolingConnManager).setDefaultRequestConfig(reqConfig).build();
            logger.info("Created a HTTP connection pool");
        }

        response = client.execute(new HttpGet(url));
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return response.getEntity().getContent();
        }
        throw new HttpException("HTTP error: " + response.getStatusLine().getStatusCode());
    }

    public static void cleanHTTPConnPool() {
        //poolingConnManager.close();
        try {
            response.close();
        } catch (IOException e) {
            logger.error("Couldn't close a HTTP response!");
            e.printStackTrace();
        }
    }

}
