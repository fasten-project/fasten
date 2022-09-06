package eu.fasten.core.data;

public class Constants {

    public static final String mvnCoordinateSeparator = ":";

    public static final String mvnForge = "mvn";

    public static final String debianForge = "debian";

    public static final String pypiForge = "PyPI";

    public static final String opalGenerator = "OPAL";

    public static final String walaGenerator = "WALA";

    public static final int transactionRestartLimit = 3;

    public static final int insertionBatchSize = 4096;

    public static final String mvnRepoEnvVariable = "MVN_REPO";

    public static final String fastenDbPassEnvVariable = "FASTEN_DBPASS";

    public static final String pgPasswordEnvVariable = "PGPASSWORD";

    public static final String defaultMavenResolutionScopes = "compile,runtime,provided";

    public static final int MIN_COMPRESSED_GRAPH_SIZE = 100;

    public static final String fastenApiUrlEnvVariable = "FASTEN_API_URL";

    public static final String fastenApiUrlDefault = "https://api.fasten.eu/api/";

    public static final String FIRST_LINE = "first";
    public static final String LAST_LINE = "last";
    public static final String IS_DEFINED = "defined";
    public static final String ACCESS_MODIFIER = "access";

    public static final String CALLSITE_LINE = "line";
    public static final String INVOCATION_TYPE = "type";
    public static final String RECEIVER_TYPE = "receiver";
}
