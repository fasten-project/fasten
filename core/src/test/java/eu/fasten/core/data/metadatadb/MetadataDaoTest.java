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

import javax.annotation.Nullable;
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

    @Test
    void givenPackageVersionInDB_whenInsertingFileLicenses_thenFileLicensesAreAppended() {

        // One license case (from scancode)
        String firstSpdxId = "BSD-3-Clause";
        String singleLicenseEntry = "{\"spdx_license_key\": \"" + firstSpdxId + "\"}";
        String singleLicenseArray = "\"licenses\": [" + singleLicenseEntry + "]";

        // Multiple licenses case (from scancode)
        String secondSpdxId = "GPL-2.0-only";
        String secondLicenseEntry = "{\"spdx_license_key\": \"" + secondSpdxId + "\"}";
        String multipleLicensesEntry = singleLicenseEntry + "," + secondLicenseEntry;
        String multipleLicensesArray = "\"licenses\": [" + multipleLicensesEntry + "]";

        // File with an empty metadata field
        String fileWithEmptyMetadataPath = "/mnt/fasten/c/com.esotericsoftware/reflectasm/src/" +
                "com/esotericsoftware/reflectasm/AccessClassLoader.java";

        // File with a not-empty metadata field
        String notEmptyMetadataField = "\"not\": \"empty\"";
        String fileWithNotEmptyMetadataPath = "/mnt/fasten/c/com.esotericsoftware/reflectasm/src/" +
                "com/esotericsoftware/reflectasm/FieldAccess.java";

        // Test coordinates (all files above belong to these coordinates)
        Revision fileCoordinates = new Revision("com.esotericsoftware", "reflectasm", "1.11.8", new Timestamp(-1));

        // Input file licenses
        JSONObject jsonObjectForFileWithOneLicense =
                new JSONObject().put("licenses", new JSONArray()
                        .put(new JSONObject().put("spdx_license_key", firstSpdxId)));
        JSONObject jsonObjectForFileWithMultipleLicenses =
                new JSONObject().put("licenses", new JSONArray()
                        .put(new JSONObject().put("spdx_license_key", firstSpdxId))
                        .put(new JSONObject().put("spdx_license_key", secondSpdxId)));

        // Expected metadata field cases
        String expectedMetadataFieldForFileWithOneLicenseWithEmptyMetadataField =
                "{" + singleLicenseArray + "}";
        String expectedMetadataFieldForFileWithOneLicenseWithNotEmptyMetadataField =
                "{" + notEmptyMetadataField + "," + singleLicenseArray + "}";
        String expectedMetadataFieldForFileWitMultipleLicensesWithEmptyMetadataField =
                "{" + multipleLicensesArray + "}";
        ;
        String expectedMetadataFieldForFileWitMultipleLicensesWithNotEmptyMetadataField =
                "{" + notEmptyMetadataField + "," + multipleLicensesArray + "}";

        // Coordinates, filePath, fileLicenses -> expected updated metadata field
        Map<Map.Entry<Revision, Map.Entry<String, String>>, String> inputToExpected = Map.ofEntries(
                // One license with empty metadata field
                Map.entry(Map.entry(
                        // coordinates
                        fileCoordinates,
                        Map.entry(
                                // filePath
                                fileWithEmptyMetadataPath,
                                // fileLicenses
                                jsonObjectForFileWithOneLicense.toString())),
                        // expectedMetadataField
                        expectedMetadataFieldForFileWithOneLicenseWithEmptyMetadataField),
                // One license with not empty metadata field
                Map.entry(Map.entry(
                        // coordinates
                        fileCoordinates,
                        Map.entry(
                                // filePath
                                fileWithNotEmptyMetadataPath,
                                // fileLicenses
                                jsonObjectForFileWithOneLicense.toString())),
                        // expectedMetadataField
                        expectedMetadataFieldForFileWithOneLicenseWithNotEmptyMetadataField),
                // Multiple licenses with empty metadata field
                Map.entry(Map.entry(
                        // coordinates
                        fileCoordinates,
                        Map.entry(
                                // filePath
                                fileWithEmptyMetadataPath,
                                // fileLicenses
                                jsonObjectForFileWithMultipleLicenses.toString())),
                        // expectedMetadataField
                        expectedMetadataFieldForFileWitMultipleLicensesWithEmptyMetadataField),
                // Multiple licenses with not empty metadata field
                Map.entry(Map.entry(
                        // coordinates
                        fileCoordinates,
                        Map.entry(
                                // filePath
                                fileWithNotEmptyMetadataPath,
                                // fileLicenses
                                jsonObjectForFileWithMultipleLicenses.toString())),
                        // expectedMetadataField
                        expectedMetadataFieldForFileWitMultipleLicensesWithNotEmptyMetadataField)
        );

        inputToExpected.forEach((input, expectedMetadataField) -> {

            // Input fields
            Revision coordinates = input.getKey();
            String filePath = input.getValue().getKey();
            String fileLicenses = input.getValue().getValue();

            // Inserting file licenses into the database
            @Nullable String updatedMetadataField = metadataDao.insertFileLicenses(coordinates, filePath, fileLicenses);

            // Checking whether the updated file's metadata field has been updated correctly or not
            if (updatedMetadataField != null) { // file exists in DB
                JSONAssert.assertEquals(
                        "File licenses have not been inserted successfully.",
                        expectedMetadataField,
                        updatedMetadataField,
                        JSONCompareMode.NON_EXTENSIBLE
                );
            }
        });
    }

    @AfterAll
    static void stopPostgresContainer() {
        postgreSQLContainer.stop();
    }
}