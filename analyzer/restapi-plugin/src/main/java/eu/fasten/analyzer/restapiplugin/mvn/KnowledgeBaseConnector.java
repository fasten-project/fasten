package eu.fasten.analyzer.restapiplugin.mvn;

import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.SQLException;

@Component
public class KnowledgeBaseConnector {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseConnector.class.getName());

    /**
     * KnowledgeBase data access object.
     */
    public static MetadataDao kbDao;

    /**
     * KnowledgeBase username, retrieved from the server configuration file.
     */
    @Value("${kb.user}")
    private String kbUser;

    /**
     * KnowledgeBase address, retrieved from the server configuration file.
     */
    @Value("${kb.url}")
    private String kbUrl;

    /**
     * Connects to the KnowledgeBase before starting the REST server.
     */
    @PostConstruct
    public void connectToKnowledgeBase() {

        logger.info("Establishing connection to the KnowledgeBase at " + kbUrl + ", user " + kbUser + "...");
        try {
            kbDao = new MetadataDao(PostgresConnector.getDSLContext(kbUrl, kbUser));
        } catch (SQLException e) {
            logger.error("Couldn't connect to the KnowledgeBase", e);
            System.exit(1);
        }
        logger.info("...KnowledgeBase connection established successfully.");
    }
}
