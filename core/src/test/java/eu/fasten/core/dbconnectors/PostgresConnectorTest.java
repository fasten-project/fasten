package eu.fasten.core.dbconnectors;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import eu.fasten.core.data.Constants;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PostgresConnectorTest {

    protected static final String KB_USERNAME = "testusername";
    protected static final String KB_PASSWORD = "testpassword";

    @ClassRule
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13.2")
            .withDatabaseName("integration-test-postgres")
            .withUsername(KB_USERNAME)
            .withPassword(KB_PASSWORD);

    @BeforeAll
    static void startPostgresContainer() {
        postgreSQLContainer.start();
        postgreSQLContainer.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1));
    }

    @Test
    public void Given_CorrectKbCredentials_When_RestServerConnectsToKb_Then_NoExceptionShouldBeThrown() {

        // Connection to DB should not throw any exceptions
        assertDoesNotThrow(() ->
                SystemLambda.withEnvironmentVariable(Constants.pgPasswordEnvVariable, KB_PASSWORD).execute(() ->
                        PostgresConnector.getDSLContext(
                                postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), true)));
    }

    @Test
    public void Given_NoKbPassword_When_RestServerConnectsToKb_Then_IllegalArgumentExceptionShouldBeThrown()
            throws SQLException {

        assertThrows(IllegalArgumentException.class, () ->
                PostgresConnector.getDSLContext(
                        postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), true));
    }

    @Test
    public void Given_WrongKbPassword_When_RestServerConnectsToKb_Then_SQLExceptionShouldBeThrown() {

        final String wrongKbPassword = "wrongpassword";

        assert wrongKbPassword.compareTo(KB_PASSWORD) != 0 :
                "Using the right KB password during a test that was supposed to be using a wrong one.";

        assertThrows(SQLException.class, () ->
                SystemLambda.withEnvironmentVariable(Constants.pgPasswordEnvVariable, wrongKbPassword).execute(() ->
                        PostgresConnector.getDSLContext(
                                postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), true)));
    }

    @AfterAll
    static void stopPostgresContainer() {
        postgreSQLContainer.stop();
    }
}
