package eu.fasten.analyzer.complianceanalyzer;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Yaml;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Plugin which runs qmstr command line tool to detect
 * license compatibility and compliance.
 */
public class ComplianceAnalyzerPlugin extends Plugin {

    /**
     * Kubernetes namespace where our demo objects are going to be deployed in.
     */
    protected static final String K8S_NAMESPACE = "default";

    public ComplianceAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class CompliancePluginExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(CompliancePluginExtension.class.getName());

        protected String consumerTopic = "fasten.RepoCloner.out";
        protected Throwable pluginError = null;
        protected String repoUrl;

        /**
         * Path to the Kubernetes cluster credentials file
         */
        protected final String clusterCredentialsFilePath;

        public CompliancePluginExtension(String clusterCredentialsFilePath) {
            this.clusterCredentialsFilePath = clusterCredentialsFilePath;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void consume(String record) {
            try { // Fasten error-handling guidelines

                this.pluginError = null;
                var consumedJson = new JSONObject(record);
                if (consumedJson.has("input")) {
                    consumedJson = consumedJson.getJSONObject("input");
                }
                this.repoUrl = consumedJson.getString("repoUrl");
                var consumedJsonPayload = new JSONObject(record);
                if (consumedJsonPayload.has("payload")) {
                    consumedJsonPayload = consumedJsonPayload.getJSONObject("payload");
                }
                String repoPath = consumedJsonPayload.getString("repoPath");
                String artifactID = consumedJsonPayload.getString("artifactId");

                logger.info("Repo url: " + repoUrl);
                logger.info("Path to the cloned repo: " + repoPath);
                logger.info("Artifact id: " + artifactID);

                if (repoUrl == null) {
                    IllegalArgumentException missingRepoUrlException =
                            new IllegalArgumentException("Invalid repository information: missing repository URL.");
                    setPluginError(missingRepoUrlException);
                    throw missingRepoUrlException;
                }

                // Connecting to the Kubernetes cluster
                connectToCluster();

                // Starting the QMSTR Job
                applyK8sJob();

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage());
                setPluginError(e);
            }

        }

        protected void connectToCluster() throws IOException {
            try {
                // If we don't specify credentials when constructing the client, the client library will
                // look for credentials via the environment variable GOOGLE_APPLICATION_CREDENTIALS.
                GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(clusterCredentialsFilePath))
                        .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

                KubeConfig.registerAuthenticator(new ReplacedGCPAuthenticator(credentials));

                ApiClient client = Config.defaultClient();
                Configuration.setDefaultApiClient(client);
            } catch (IOException e) {
                throw new IOException("Couldn't find cluster credentials at: " + clusterCredentialsFilePath);
            }
        }

        protected void applyK8sJob() throws ApiException, IOException {

            try {

                // Deploying the QMSTR ConfigMap
                String configMapFilePath = "/docker/master-config.yaml";
                File configMapFile = new File(ComplianceAnalyzerPlugin.class.getResource(configMapFilePath).getPath());
                V1ConfigMap configMap = Yaml.loadAs(configMapFile, V1ConfigMap.class);
                V1ConfigMap deployedConfigMap = new CoreV1Api().createNamespacedConfigMap(K8S_NAMESPACE, configMap, null, null, null);
                logger.info("Deployed ConfigMap: " + deployedConfigMap);

                // Deploying the QMSTR Service
                String serviceFilePath = "/docker/service.yaml";
                File serviceFile = new File(ComplianceAnalyzerPlugin.class.getResource(serviceFilePath).getPath());
                V1Service service = Yaml.loadAs(serviceFile, V1Service.class);
                V1Service deployedService = new CoreV1Api().createNamespacedService(K8S_NAMESPACE, service, null, null, null);
                logger.info("Deployed Service: " + deployedService);

                // Patching the QMSTR Job
                String jobFilePath = "/docker/job.yaml";
                String jobFileFullPath = ComplianceAnalyzerPlugin.class.getResource(jobFilePath).getPath();
                Path jobFileSystemPath = Paths.get(jobFileFullPath);
                Charset jobFileCharset = StandardCharsets.UTF_8;
                String jobFileContent = Files.readString(jobFileSystemPath, jobFileCharset);
                jobFileContent = jobFileContent.replaceAll("url", repoUrl);
                Files.write(jobFileSystemPath, jobFileContent.getBytes(jobFileCharset));

                // Deploying the QMSTR Job
                Yaml.addModelMap("v1", "Job", V1Job.class);
                File jobFile = new File(jobFileFullPath);
                V1Job yamlJob = Yaml.loadAs(jobFile, V1Job.class);
                V1Job deployedJob = new BatchV1Api().createNamespacedJob(K8S_NAMESPACE, yamlJob, null, null, null);
                logger.info("Deployed Job: " + deployedJob);

            } catch (IOException e) {
                throw new IOException("Exception while patching the QMSTR Job: " + e.getMessage());
            } catch (ApiException e) {
                throw new ApiException("Exception while deploying a Kubernetes object: " + e.getMessage());
            }

        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            // TODO
            throw new UnsupportedOperationException(
                    "Output path will become available as soon as the QMSTR reporting phase will be released."
            );
        }

        @Override
        public String name() {
            return "License Compliance Plugin";
        }

        @Override
        public String description() {
            return "License Compliance Plugin."
                    + "Consumes Repository Urls from Kafka topic,"
                    + " connects to cluster and starts a Kubernetes Job."
                    + " The Job spins a qmstr process which detects the project's"
                    + " license compliance and compatibility."
                    + " Once the Job is done the output is written to another Kafka topic.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }
    }
}
