package eu.fasten.analyzer.complianceanalyzer;

import org.pf4j.PluginWrapper;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.pf4j.Extension;

import eu.fasten.core.plugins.DBConnector;
import java.io.IOException;
import java.io.File;
import org.jooq.DSLContext;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.util.Yaml;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import java.io.FileInputStream;
import io.kubernetes.client.util.Config;

public class ComplianceAnalyzerPlugin extends Plugin {

    public ComplianceAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    // Connect to Kubernetes
    // Start qmstr master
    // Spin build/analysis/reporting phase

    @Extension
    public static class CompliancePluginExtension implements DBConnector {
        private static DSLContext dslContext;
         private Throwable pluginError = null;
         private final Logger logger = LoggerFactory.getLogger(CompliancePluginExtension.class.getName());


         @Override
         public void setDBConnection(DSLContext dslContext) {
            CompliancePluginExtension.dslContext = dslContext;
         }

         @Override
         public String name() {
             return "License compliance Plugin";
         }

          @Override
         public String description() {
             // TODO: Update the description
             return "License Compliance Plugin";
         }

          @Override
         public String version() {
             return "0.0.1";
         }

         @Override
        public void start() {
            //Connect to Kubernetes

            String jsonPath = "./fasten-ca08747cdc4f.json";

            try {

                // If we don't specify credentials when constructing the client, the client library will
                // look for credentials via the environment variable GOOGLE_APPLICATION_CREDENTIALS.
                GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(jsonPath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

                KubeConfig.registerAuthenticator(new ReplacedGCPAuthenticator(credentials));

                ApiClient client = Config.defaultClient();
                Configuration.setDefaultApiClient(client);

                Yaml.addModelMap("v1", "Job", V1Job.class);

                File file = new File("docker/base/job.yaml");
                V1Job yamlJob = (V1Job) Yaml.load(file);
                System.out.println("Job definition: " + yamlJob);
                BatchV1Api api = new BatchV1Api();

                V1Job createResult = api.createNamespacedJob("default", yamlJob, null, null, null);
                System.out.println(createResult);

                /*
                V1Status deleteResult = api.deleteNamespacedJob(yamlJob.getMetadata().getName(),
                "default", null, null, null, null, null, new V1DeleteOptions());
                System.out.println(deleteResult);
                s*/

            } catch (IOException ex) {
                System.out.println(ex.toString());
                System.out.println("Could not find file " + jsonPath);
            } catch (ApiException ex) {
                System.err.println("Status code: " + ex.getCode());
                System.err.println("Reason: " + ex.getResponseBody());
                System.err.println("Response headers: " + ex.getResponseHeaders());
                ex.printStackTrace();
            }

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
