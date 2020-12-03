package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.ResolutionApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.Revision;
import org.json.JSONArray;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ResolutionApiServiceImpl implements ResolutionApiService {

    @Override
    public ResponseEntity<String> resolveDependencies(String package_name, String version, boolean transitive, long timestamp) {
        var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
        var depSet = KnowledgeBaseConnector.graphResolver.resolveDependencies(groupId,
                artifactId, version, timestamp, KnowledgeBaseConnector.dbContext, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(Revision::toJSON).forEach(jsonArray::put);
        return new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> resolveDependents(String package_name, String version, boolean transitive, long timestamp) {
        var groupId = package_name.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = package_name.split(Constants.mvnCoordinateSeparator)[1];
        var depSet = KnowledgeBaseConnector.graphResolver.resolveDependents(groupId,
                artifactId, version, timestamp, transitive);
        var jsonArray = new JSONArray();
        depSet.stream().map(Revision::toJSON).forEach(jsonArray::put);
        return new ResponseEntity<>(jsonArray.toString(), HttpStatus.OK);
    }
}
