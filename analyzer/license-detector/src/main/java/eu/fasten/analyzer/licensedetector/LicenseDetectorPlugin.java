package eu.fasten.analyzer.licensedetector;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;
import eu.fasten.analyzer.licensedetector.utils.JSONUtils;
import eu.fasten.core.plugins.KafkaPlugin;


public class LicenseDetectorPlugin extends Plugin {

    public LicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class LicenseDetector implements KafkaPlugin {

        private static final String PLUGIN_VERSION = "0.1.0";
        private static final DetectedLicenses EMPTY_DETECTED_LICENSES = new DetectedLicenses();

		private final Logger logger = LoggerFactory.getLogger(LicenseDetector.class.getName());

        protected Exception pluginError = null;

        protected DetectedLicenses detectedLicenses;
        
        protected List<String> consumeTopics;
        
        protected JSONObject payload;
        
        protected String javaRepoPathKey;
        protected String javaRepoUrlKey;
        protected String pythonRepoPathKey;
        protected String pythonRepoUrlKey;
        protected String cRepoPathKey;
        protected String cRepoUrlKey;
        protected String pathReplace;

        public LicenseDetector() {
        	this.javaRepoPathKey = defaultEnv("JAVA_REPO_PATH_KEY", "fasten.RepoCloner.out/payload/repoPath");
        	this.javaRepoUrlKey = defaultEnv("JAVA_REPO_URL_KEY", "fasten.RepoCloner.out/payload/repoUrl");
        	this.pythonRepoPathKey = defaultEnv("PYTHON_REPO_PATH_KEY", "fasten.PyCG.out/payload/sourcePath");
        	this.pythonRepoUrlKey = defaultEnv("PYTHON_REPO_URL_KEY", "fasten.PyCG.out/payload/repoUrl");
        	this.cRepoPathKey = defaultEnv("C_REPO_PATH_KEY", "fasten.cscout.out/payload/sourcePath");
        	this.pathReplace = StringUtils.removeEnd(defaultEnv("FASTEN_MNT_REPLACE", ""), "/");
		}
        
        private String defaultEnv(String name, String def) {
        	String v = System.getenv(name);
        	return v == null ? def : v;
        }

		@Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(consumeTopics);
        }
        
        @Override
        public void setTopic(String topicName) {
        	this.consumeTopics = Arrays.asList(topicName.trim().split("\\s*;\\s*"));
        }
       

        @Override
        public void consume(String record) {
            try {
                pluginError = null;
                detectedLicenses = null;
                payload = new JSONObject(record);
            	LanguageType lang = detectLanguage();
            	ILicenseDetector licenseDetector = LicenseDetectorFactory.create(lang);
            	RepoCoords repoCoords = extractRepoCoords(lang);
                // TODO Checking whether the repository has already been scanned (by querying the KB)
            	detectedLicenses = licenseDetector.detect(repoCoords.path, repoCoords.url);
            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }
        
        private LanguageType detectLanguage() {
        	String pluginName = payload.optString("plugin_name");
        	if (payload.has("fasten.cscout.out") || "CScoutKafkaPlugin".equals(pluginName))
        		return LanguageType.C;
        	if (payload.has("fasten.PyCG.out") || "PyCG".equals(pluginName))
        		return LanguageType.PYTHON;
        	if (payload.has("fasten.RepoCloner.out") ||  "RepoCloner".equals(pluginName))
				return LanguageType.JAVA;
			throw new LicenseDetectorException("Unable to detect language from the received record");
        	
        }
        
        @Override
        public Optional<String> produce() {
            if (detectedLicenses == null){
                return Optional.of(new JSONObject(EMPTY_DETECTED_LICENSES).toString());
            } else {
            	String json = new JSONObject(detectedLicenses).toString();
            	logger.info("producing -> {}", json);
                return Optional.of(json);
            }
        }
       
        @Override
        public String getOutputPath() {
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
            return PLUGIN_VERSION;
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
        public long getMaxConsumeTimeout() {
            return 30 * 60 * 1000; // 30 minutes
        }
        
		protected RepoCoords extractRepoCoords(LanguageType lang) {
            String repoPath, repoUrl;
			switch (lang) {
			case JAVA:
                repoPath = JSONUtils.optStringByPath(payload, javaRepoPathKey, "");
                repoUrl =  JSONUtils.optStringByPath(payload, javaRepoUrlKey, "");
                break;
			case PYTHON:
                repoPath = JSONUtils.optStringByPath(payload, pythonRepoPathKey, "");
                repoUrl =  JSONUtils.optStringByPath(payload, pythonRepoUrlKey, "");
                break;
			case C:
                repoPath = JSONUtils.optStringByPath(payload, cRepoPathKey, "");
                repoUrl =  JSONUtils.optStringByPath(payload, cRepoUrlKey, "");
                break;
			default:
				throw new IllegalArgumentException("Invalid LanguageType");
			}
            return new RepoCoords(repoPath, lang, repoUrl);
    	}        
		
        public class RepoCoords {
        	String path;
        	String url;
        	
        	private RepoCoords(String path, LanguageType lang, String url) {
        		boolean hasPath = StringUtils.isNotBlank(path);
        		if (hasPath) {
	        		path = StringUtils.removeStart(path, "/mnt/fasten/");
	        		if (StringUtils.isNotBlank(pathReplace)) {
	        			path = pathReplace + "/" + lang.name().toLowerCase() + "/" + path;
	        		} else {
	        			path = "/mnt/fasten/" + lang.name().toLowerCase() + "/" + path;	
	        		}
        		}
				this.path = path;
				this.url = url;
			}

			@Override
			public String toString() {
				return "RepoCoords [path=" + path + ", url=" + url + "]";
			}

        	
        }
        
    }
}
