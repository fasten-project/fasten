package eu.fasten.analyzer.licensefeeder;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import eu.fasten.core.data.Constants;
import eu.fasten.core.dbconnectors.PostgresConnector;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;

class LicenseFeederIntegrationTest {

    protected static final String KB_USERNAME = "fasten";
    protected static final String KB_PASSWORD = "testpassword";

    @ClassRule
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13.2")
            .withDatabaseName("metadatadao-test")
            .withUsername(KB_USERNAME)
            .withPassword(KB_PASSWORD);

    @BeforeAll
    public static void startPostgresInstance() {
        postgreSQLContainer.start();
        postgreSQLContainer.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1));
    }

    @Test
    public void givenLicenseFeederInstance_whenLicenseFeederIsCreated_thenLicenseFeederCanConnectToMetadataDatabase()
            throws Exception {

        var licenseFeeder = new LicenseFeederPlugin.LicenseFeeder();
        SystemLambda.withEnvironmentVariable(Constants.fastenDbPassEnvVariable, KB_PASSWORD).execute(() ->

                // No exception should be thrown while connecting to the DB
                licenseFeeder.setDBConnection(Collections.singletonMap(Constants.mvnForge,
                        PostgresConnector.getDSLContext(
                                postgreSQLContainer.getJdbcUrl(),
                                postgreSQLContainer.getUsername(),
                                true
                        ))));
    }

    @AfterAll
    static void stopPostgresContainer() {
        postgreSQLContainer.stop();
    }
}