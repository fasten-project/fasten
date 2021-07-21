package eu.fasten.core.data.metadatadb;

import com.google.common.collect.Sets;
import eu.fasten.core.data.metadatadb.license.DetectedLicense;
import eu.fasten.core.data.metadatadb.license.DetectedLicenseSource;
import eu.fasten.core.data.metadatadb.license.DetectedLicenses;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

class MetadataDaoTest {

    protected static final String KB_USERNAME = "fasten";
    protected static final String KB_PASSWORD = "testpassword";
    protected static MetadataDao metadataDao = null;

    @ClassRule
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13.2")
            .withDatabaseName("metadatadao-test")
            .withUsername(KB_USERNAME)
            .withPassword(KB_PASSWORD)
            .withInitScript("metadatadao/com.esotericsoftware:reflectasm:1.11.18/dump.sql");

    @BeforeAll
    static void getMetadataDao() throws SQLException {
        postgreSQLContainer.start();
        postgreSQLContainer.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1));
        Connection conn = DriverManager.getConnection(
                postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword());
        DSLContext dslContext = DSL.using(conn, SQLDialect.POSTGRES);
        metadataDao = new MetadataDao(dslContext);
    }

    @Test
    void givenPackageVersionInDB_whenInsertingOutboundLicenses_thenOutboundLicensesAreAppended() {

        // Outbound license cases
        DetectedLicense oneOutboundLicense = new DetectedLicense("apache-2.0", DetectedLicenseSource.GITHUB);
        Set<DetectedLicense> oneOutboundLicenseSet = Sets.newHashSet(
                new DetectedLicense("apache-2.0", DetectedLicenseSource.GITHUB));
        DetectedLicense otherOutboundLicense = new DetectedLicense("MIT", DetectedLicenseSource.LOCAL_POM);
        Set<DetectedLicense> multipleOutboundLicensesSet = new HashSet<>(oneOutboundLicenseSet);
        multipleOutboundLicensesSet.add(otherOutboundLicense);

        // Coordinate cases
        Revision coordinateWithNoMetadata = new Revision("external_callables_library", "", "0.0.1", new Timestamp(-1));
        Revision coordinateWithMetadata = new Revision(
                "com.esotericsoftware", "reflectasm", "1.11.8", new Timestamp(-1));

        // Expected metadata field cases
        String oneOutboundLicenseJsonArrayContent = "{" +
                "\"name\": \"" + oneOutboundLicense.getName() + "\"," +
                "\"source\": \"" + oneOutboundLicense.getSource().name() + "\"" +
                "}";
        String multipleOutboundLicensesJsonArrayContent = oneOutboundLicenseJsonArrayContent + "," +
                "{" +
                "\"name\": \"" + otherOutboundLicense.getName() + "\"," +
                "\"source\": \"" + otherOutboundLicense.getSource().name() + "\"" +
                "}";
        String coordinatesWithMetadataOtherFields = "\"commitTag\": \"\"," +
                "\"sourcesUrl\": " +
                "\"https://repo.maven.apache.org/maven2/com/esotericsoftware/reflectasm/1.11.8/" +
                "reflectasm-1.11.8-sources.jar\"," +
                "\"packagingType\": \"bundle\"," +
                "\"parentCoordinate\": \"org.sonatype.oss:oss-parent:7\"," +
                "\"dependencyManagement\": {\"dependencies\": []}";

        // Input coordinates + detected outbound licenses -> expected updated metadata field
        Map<Map.Entry<Revision, Set<DetectedLicense>>, String> inputToExpected = Map.ofEntries(

                // Empty metadata field, one outbound license
                Map.entry(Map.entry(coordinateWithNoMetadata, oneOutboundLicenseSet),
                        "{\"outbound\": [" + oneOutboundLicenseJsonArrayContent + "]}"
                ),

                // Empty metadata field, multiple outbound licenses
                Map.entry(Map.entry(coordinateWithNoMetadata, multipleOutboundLicensesSet),
                        "{\"outbound\": [" + multipleOutboundLicensesJsonArrayContent + "]}"
                ),

                // Metadata field not empty, one outbound license
                Map.entry(Map.entry(coordinateWithMetadata, oneOutboundLicenseSet),
                        "{" + coordinatesWithMetadataOtherFields + "," +
                                "\"outbound\": [" + oneOutboundLicenseJsonArrayContent + "]}"
                ),

                // Metadata field not empty, multiple outbound licenses
                Map.entry(Map.entry(coordinateWithMetadata, multipleOutboundLicensesSet),
                        "{" + coordinatesWithMetadataOtherFields + "," +
                                "\"outbound\": [" + multipleOutboundLicensesJsonArrayContent + "]}"
                )
        );

        inputToExpected.forEach((input, expectedMetadataField) -> {

            // Input fields
            Revision coordinates = input.getKey();
            Set<DetectedLicense> licenses = input.getValue();

            // Inserting the outbound license into the database
            DetectedLicenses outboundLicenses = new DetectedLicenses();
            outboundLicenses.setOutbound(licenses);
            String updatedMetadata = metadataDao.insertPackageOutboundLicenses(
                    coordinates,
                    outboundLicenses.generateOutboundJson()
            );

            // Checking whether the updated package version's metadata field has been updated correctly or not
            JSONAssert.assertEquals(
                    "The returned metadata field has not been updated successfully.",
                    expectedMetadataField,
                    updatedMetadata,
                    JSONCompareMode.NON_EXTENSIBLE
            );

            // Querying the database again, making sure that not only the previously-returned metadata was updated
            JSONObject packageMetadataQueryResponse = new JSONObject(metadataDao.getPackageMetadata(
                    MavenUtilities.getMavenCoordinateName(coordinates.groupId, coordinates.artifactId),
                    coordinates.version.toString()
            ));
            if (!packageMetadataQueryResponse.has("metadata")) {
                fail("Package version's metadata query didn't return any metadata.");
            }
            JSONObject retrievedPackageMetadata = packageMetadataQueryResponse.getJSONObject("metadata");
            if (!retrievedPackageMetadata.has("outbound")) {
                fail("Package version's metedata doesn't have outbound licenses.");
            }
            JSONArray retrievedOutboundLicenses = retrievedPackageMetadata.getJSONArray("outbound");
            System.out.println("retrievedOutboundLicenses: " + retrievedOutboundLicenses);
            JSONAssert.assertEquals(
                    "Outbound licenses have not been inserted successfully.",
                    new JSONObject(expectedMetadataField).getJSONArray("outbound"),
                    retrievedOutboundLicenses,
                    JSONCompareMode.NON_EXTENSIBLE
            );
        });
    }

    @AfterAll
    static void stopPostgresContainer() {
        postgreSQLContainer.stop();
    }
}