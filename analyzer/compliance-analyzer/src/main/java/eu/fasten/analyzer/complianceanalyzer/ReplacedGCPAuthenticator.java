package eu.fasten.analyzer.complianceanalyzer;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.authenticators.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class ReplacedGCPAuthenticator implements Authenticator {
    private static final Logger log;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String EXPIRY = "expiry";

    static {
        log = LoggerFactory.getLogger(io.kubernetes.client.util.authenticators.GCPAuthenticator.class);
    }

    private final GoogleCredentials credentials;

    public ReplacedGCPAuthenticator(GoogleCredentials credentials) {
        this.credentials = credentials;
    }

    public String getName() {
        return "gcp";
    }

    public String getToken(Map<String, Object> config) {
        return (String) config.get("access-token");
    }

    public boolean isExpired(Map<String, Object> config) {
        Object expiryObj = config.get("expiry");
        Instant expiry = null;
        if (expiryObj instanceof Date) {
            expiry = ((Date) expiryObj).toInstant();
        } else if (expiryObj instanceof Instant) {
            expiry = (Instant) expiryObj;
        } else {
            if (!(expiryObj instanceof String)) {
                throw new RuntimeException("Unexpected object type: " + expiryObj.getClass());
            }

            expiry = Instant.parse((String) expiryObj);
        }

        return expiry != null && expiry.compareTo(Instant.now()) <= 0;
    }

    public Map<String, Object> refresh(Map<String, Object> config) {
        try {
            AccessToken accessToken = this.credentials.refreshAccessToken();

            config.put(ACCESS_TOKEN, accessToken.getTokenValue());
            config.put(EXPIRY, accessToken.getExpirationTime());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return config;
    }
}