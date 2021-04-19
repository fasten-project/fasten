package eu.fasten.analyzer.licensedetector;

import eu.fasten.core.plugins.KafkaPlugin;
import io.dgraph.DgraphClient;
import io.dgraph.DgraphGrpc;
import io.dgraph.DgraphProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;


public class LicenseDetectorPlugin extends Plugin {

    public LicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class LicenseDetector implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(LicenseDetector.class.getName());

        protected Exception pluginError = null;

        /**
         * The topic this plugin consumes.
         */
        protected String consumerTopic = "fasten.RepoCloner.out";

        /**
         * Patch to be applied to pom.xml files before the analysis begins.
         */
        protected static final String POM_PATCH_FILE = "/pom-patch.xml";

        /**
         * Temporary file used when retrieving the patch file from within a fat JAR.
         */
        private static final String TMP_POM_PATCH_FILE = "tmp-pom-patch.xml";

        /**
         * DGraph database address.
         */
        protected static final String DGRAPH_ADDRESS =
                System.getenv("DGRAPH_ADDRESS") == null ? "dgraph" : System.getenv("DGRAPH_ADDRESS");

        /**
         * DGraph database port.
         */
        protected static final int DGRAPH_PORT;

        static {
            // Retrieving DGraph's port
            int retrievedDgraphPort;
            if (System.getenv("DGRAPH_PORT") == null) {
                retrievedDgraphPort = 9080;
            } else {
                try {
                    retrievedDgraphPort = Integer.parseInt(System.getenv("DGRAPH_PORT"));
                } catch (NumberFormatException e) {
                    retrievedDgraphPort = 9080;
                }
            }
            DGRAPH_PORT = retrievedDgraphPort;
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

                logger.info("License detector started.");

                // Retrieving the repository path on the shared volume
                String repoPath = extractRepoPath(record);
                logger.info("License detector: scanning repository in " + repoPath + "...");

                // Injecting the Quartermaster Maven plugin
                patchPomFile(repoPath);

                // Dropping DGraph data
                dropDgraphDatabase();

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage());
                setPluginError(e);
            }
        }

        /**
         * Retrieves the cloned repository path on the shared volume from the input record.
         *
         * @param record the input record containing repository information.
         * @return the repository path on the shared volume
         * @throws IllegalArgumentException in case the function couldn't find the repository path in the input record.
         */
        protected String extractRepoPath(String record) throws IllegalArgumentException {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            String repoPath = payload.getString("repoPath");
            if (repoPath == null) {
                throw new IllegalArgumentException("Invalid repository information: missing repository path.");
            }
            return repoPath;
        }

        /**
         * Retrieves the pom.xml file given a repository path.
         *
         * @param repoPath the repository path whose pom.xml file must be retrieved.
         * @return the pom.xml file of the repository.
         */
        protected Optional<File> retrievePomFile(String repoPath) {

            // Result
            Optional<File> pomFile = Optional.empty();

            // Repository folder
            File repoFolder = new File(repoPath);

            // Retrieving all repository's pom files
            File[] pomFiles = repoFolder.listFiles((dir, name) -> name.equalsIgnoreCase("pom.xml"));
            if (pomFiles == null) {
                throw new RuntimeException(repoPath + " does not denote a directory.");
            }
            logger.info("Found " + pomFiles.length + " pom.xml file" +
                    ((pomFiles.length == 1) ? "" : "s") + ": " + Arrays.toString(pomFiles));
            if (pomFiles.length == 0) {
                logger.error("No pom.xml file found in " + repoFolder.getAbsolutePath() + ".");
            } else if (pomFiles.length == 1) {
                pomFile = Optional.ofNullable(pomFiles[0]);
            } else {
                // Retrieving the pom.xml file having the shortest path (closest to it repository's root path)
                pomFile = Arrays.stream(pomFiles).min(Comparator.comparingInt(f -> f.getAbsolutePath().length()));
                logger.info("Multiple pom.xml files found. Using " + pomFile.get());
            }

            return pomFile;
        }

        /**
         * Patches a pom.xml file by injecting the Quartermaster Maven plugin.
         *
         * @param repoPath the path of the repository whose pom.xml file needs to be patched.
         * @throws ParserConfigurationException in case the XML DocumentBuilder could not be instantiated.
         * @throws IOException                  in case either the input pom.xml file or the patch one
         *                                      could not be found.
         * @throws URISyntaxException           in case either the patch XML file could not be found.
         * @throws TransformerException         in case of error while overwriting the XML file.
         */
        protected void patchPomFile(String repoPath)
                throws ParserConfigurationException, IOException, URISyntaxException, TransformerException {

            // Retrieving the pom file
            Optional<File> pomFile = retrievePomFile(repoPath);
            if (pomFile.isEmpty()) {
                throw new FileNotFoundException("No file named pom.xml found in " + repoPath + ". " +
                        "This plugin only analyzes Maven projects.");
            }
            logger.info("Patching " + pomFile.get().getAbsolutePath() + "...");

            // Retrieving the patch XML file
            File patchFile = new File(TMP_POM_PATCH_FILE);
            try (var patchFileStream = getClass().getResourceAsStream(POM_PATCH_FILE);
                 OutputStream outputStream = new FileOutputStream(patchFile)) {

                IOUtils.copy(patchFileStream, outputStream);
            }
            if (new BufferedReader(new FileReader(patchFile)).readLine() == null) { // shouldn't be empty
                throw new FileNotFoundException("Patch XML file could not be found.");
            }

            // XML document builder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    logger.warn(exception.getMessage());
                }

                @Override
                public void error(SAXParseException exception) {
                    logger.error(exception.getMessage());
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    logger.error("Fatal: " + exception.getMessage());
                }
            });

            // Checking whether the patch is malformed or not
            Document patchDocument = getXmlDocument(builder, patchFile, "patch XML");

            // Parsing the repository pom file
            Document repoPomDocument = getXmlDocument(builder, pomFile.get(), "repository pom.xml");

            // Retrieving the `plugins` XML section
            Element documentRoot = repoPomDocument.getDocumentElement();
            if (documentRoot == null) {
                throw new IllegalArgumentException("No root element in repository pom.xml file "
                        + pomFile.get().getAbsolutePath() + ".");
            }
            NodeList buildNodeList = documentRoot.getElementsByTagName("build");
            if (buildNodeList.getLength() == 0) {
                documentRoot.appendChild(repoPomDocument.createElement("build"));
            }
            Element buildElement = (Element) buildNodeList.item(0);
            NodeList pluginsNodeList = buildElement.getElementsByTagName("plugins");
            if (pluginsNodeList.getLength() == 0) {
                buildElement.appendChild(repoPomDocument.createElement("plugins"));
            }
            Element pluginsElement = (Element) pluginsNodeList.item(0);

            // Insertion
            logger.info("Injecting the Quartermaster build plugin...");
            Node importedNode = repoPomDocument.importNode(patchDocument.getDocumentElement(), true);
            pluginsElement.appendChild(importedNode);
            logger.info("...Quartermaster build plugin injected.");

            // Saving the file
            logger.info("Overwriting " + pomFile.get().getAbsolutePath() + "...");
            writeXmlToFile(documentRoot, pomFile.get());
            logger.info("..." + pomFile.get().getAbsolutePath() + " successfully patched.");
        }

        /**
         * Retrieves an XML document given its file.
         *
         * @param builder  the XML builder being used.
         * @param file     the XML file whose document is of interest.
         * @param fileName a file name used for debugging purposes.
         * @return the XML document of interest.
         * @throws FileNotFoundException in case the file could not be found.
         * @throws RuntimeException      in case the XML file could not be parsed.
         */
        protected Document getXmlDocument(DocumentBuilder builder, File file, String fileName)
                throws FileNotFoundException, RuntimeException {

            Document document = null;
            try {
                document = builder.parse(file); // also checks whether the file is malformed or not
            } catch (SAXException e) {
                throw new IllegalArgumentException(
                        fileName.substring(0, 1).toUpperCase() + fileName.substring(1) + " file is malformed.");
            } catch (IOException e) {
                throw new FileNotFoundException(
                        fileName.substring(0, 1).toUpperCase() + fileName.substring(1) + " file not found.");
            }
            if (document == null) {
                throw new RuntimeException("Couldn't parse the " + fileName + " file.");
            }
            return document;
        }

        /**
         * Writes an XML document to a file.
         *
         * @param document the XML element to be written.
         * @param file     the file to be overridden.
         * @throws TransformerException in case of error while creating an XML `Transformer` or
         *                              while transforming the XML file.
         */
        protected void writeXmlToFile(Element document, File file) throws TransformerException {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes"); // FIXME Unnecessary empty lines between all lines
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource domSource = new DOMSource(document);
            StreamResult sr = new StreamResult(file);
            tf.transform(domSource, sr);
        }

        /**
         * Drops the entire content of a DGraph instance.
         */
        protected void dropDgraphDatabase() {
            logger.info("Dropping content of the DGraph instance at " + DGRAPH_ADDRESS + ":" + DGRAPH_PORT + "...");
            ManagedChannel channel = ManagedChannelBuilder.forAddress(DGRAPH_ADDRESS, DGRAPH_PORT)
                    .usePlaintext().build();
            DgraphGrpc.DgraphStub stub = DgraphGrpc.newStub(channel);
            DgraphClient dgraphClient = new DgraphClient(stub);
            dgraphClient.alter(DgraphProto.Operation.newBuilder().setDropAll(true).build());
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty(); // this plugin only inserts data into the Metadata DB
        }

        @Override
        public String getOutputPath() {
            /*  A JSON file with detected licenses is available in another container.
                Licenses are inserted into the Metadata DB. */
            return null;
        }

        @Override
        public String name() {
            return "License Detector Plugin";
        }

        @Override
        public String description() {
            return "Detects licenses at the file level";
        }

        @Override
        public String version() {
            return "0.0.2";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }

        @Override
        public boolean isStaticMembership() {
            /*  The Pod behind license detection contains containers that are supposed to terminate
                upon consuming one record. This avoids rebalancing. */
            return true;
        }

        @Override
        public long getMaxConsumeTimeout() {
            return 1 * 60 * 60 * 1000; // FIXME 1 hour
        }
    }
}
