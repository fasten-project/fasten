package eu.fasten.analyzer.complianceanalyzer;

import org.pf4j.PluginWrapper;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.pf4j.Extension;

import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.core.data.graphdb.RocksDao;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import org.jooq.DSLContext;
import org.json.JSONObject;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.util.Yaml;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;

import io.kubernetes.client.util.Config;


/**
 *   Plugin which runs qmstr command line tool to detect
 *   license compatibility and compliance.
 */
public class ComplianceAnalyzerPlugin extends Plugin {

    public ComplianceAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class CompliancePluginExtension implements KafkaPlugin, DBConnector {
        private String consumerTopic = "fasten.RepoCloner.out";
        private static DSLContext dslContext;
        private boolean processedRecord = false;
        private Throwable pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(CompliancePluginExtension.class.getName());
        private static RocksDao rocksDao;
        private String outputPath;

        private String artifactID;
        private String repoUrl;
        private String repoPath;


        public void setRocksDao(RocksDao rocksDao) {
            CompliancePluginExtension.rocksDao = rocksDao;
        }

         @Override
         public void setDBConnection(DSLContext dslContext) {
            CompliancePluginExtension.dslContext = dslContext;
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
            this.processedRecord = false;
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
            this.repoPath = consumedJsonPayload.getString("repoPath");
            this.artifactID = consumedJsonPayload.getString("artifactId");

            logger.info("Repo url: " + repoUrl);
            logger.info("Path to the cloned repo: " + repoPath);
            logger.info("Artifact id: " + artifactID);

            if (repoUrl == null) {
                logger.info("No available URL for the specified project.\n");
                logger.info("Skipping analysis....");
                return;
            }

            /**
             * Connect to Cluster and
             * start a Kubernetes Job
             */

            ConnectToCluster();
            CreateJobConfig();
            ApplyK8sJob();

            processedRecord = true;
        }

        public void ConnectToCluster() {
            // Google Cloud credentials
            String jsonPath = "./fasten-ca08747cdc4f.json";

            try {
                // If we don't specify credentials when constructing the client, the client library will
                // look for credentials via the environment variable GOOGLE_APPLICATION_CREDENTIALS.
                GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(jsonPath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

                KubeConfig.registerAuthenticator(new ReplacedGCPAuthenticator(credentials));

                ApiClient client = Config.defaultClient();
                Configuration.setDefaultApiClient(client);
            } catch (IOException ex) {
                logger.info(ex.toString());
                logger.info("Could not find file " + jsonPath);
            }
        }

        public void CreateJobConfig(){
            try {
                // Modify default job config to include the
                // project's info
                String input1 = " -e s#java-plugin#java-plugin-" + artifactID + "#";
                String input2 = " -e s#url#" + repoUrl + "#";
                String input3 = " -e s#project#" + artifactID + "#";
                String command = "sed" + input1 + input2 + input3 + " docker/job.yaml";

                Process p = Runtime.getRuntime().exec(command);

                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String s = null;

                File f = new File("newjob.yaml");
                FileWriter fr = new FileWriter(f);
                BufferedWriter br  = new BufferedWriter(fr);

                // write the sed ouput in newjob.yaml
                while ((s = stdInput.readLine()) != null) {
                    br.write(s);
                    br.newLine();
                }

                br.close();

                // read any errors from the attempted command
                System.out.println("Here is the standard error of the command (if any):\n");
                while ((s = stdError.readLine()) != null) {
                    System.out.println(s);
                }
            }  catch (IOException ex) {
                logger.info(ex.toString());
                ex.printStackTrace();
            }
        }

        public void ApplyK8sJob() {
            try {
                // Define and apply a qmstr kubernetes job
                Yaml.addModelMap("v1", "Job", V1Job.class);

                File file = new File("./newjob.yaml");
                V1Job yamlJob = (V1Job) Yaml.load(file);
                logger.info("Job definition: " + yamlJob);
                BatchV1Api api = new BatchV1Api();

                V1Job result = api.createNamespacedJob("default", yamlJob, null, null, null);
                System.out.println(result);

                // delete job
                /*
                V1Status deleteResult = api.deleteNamespacedJob(yamlJob.getMetadata().getName(),
                "default", null, null, null, null, null, new V1DeleteOptions());
                logger.info(deleteResult);
                s*/
            } catch (IOException ex) {
                logger.info(ex.toString());
                logger.info("Could not find file: newjob.yaml");
            } catch (ApiException ex) {
                logger.error("Status code: " + ex.getCode());
                logger.error("Reason: " + ex.getResponseBody());
                logger.error("Response headers: " + ex.getResponseHeaders());
                ex.printStackTrace();
            }
        }

        @Override
        public Optional<String> produce() {
            // dump output for now
            return Optional.of("License Compliance Plugin finished successfully");
        }

        @Override
        public String getOutputPath() {
            return this.outputPath;
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


        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

         @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {
         }
    }
}
