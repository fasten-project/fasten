/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.LazyIngestionProvider;
import eu.fasten.analyzer.restapiplugin.mvn.api.StitchingApiService;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import eu.fasten.core.utils.FastenUriUtils;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StitchingApiServiceImpl implements StitchingApiService {

    private static final Logger logger = LoggerFactory.getLogger(StitchingApiServiceImpl.class);

    @Override
    public ResponseEntity<String> resolveCallablesToUris(List<Long> gidList) {
        var fastenUris = KnowledgeBaseConnector.kbDao.getFullFastenUris(gidList);
        var json = new JSONObject();
        fastenUris.forEach((key, value) -> json.put(String.valueOf(key), value));
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getCallablesMetadata(List<String> fullFastenUris, boolean allAttributes, List<String> attributes) {
        if (!allAttributes && attributes == null) {
            return new ResponseEntity<>("Either 'allAttributes' must be 'true' or a list of 'attributes' must be provided", HttpStatus.BAD_REQUEST);
        }
        Map<String, List<String>> packageVersionUris;
        try {
            packageVersionUris = fullFastenUris.stream().map(FastenUriUtils::parseFullFastenUri).collect(Collectors.toMap(
                    x -> x.get(0) + "!" + x.get(1) + "$" + x.get(2),
                    y -> List.of(y.get(3)),
                    (x, y) -> {
                        var z = new ArrayList<String>();
                        z.addAll(x);
                        z.addAll(y);
                        return z;
                    }));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        var metadataMap = new HashMap<String, JSONObject>(fullFastenUris.size());
        for (var artifact : packageVersionUris.keySet()) {
            var forge = artifact.split("!")[0];
            var forgelessArtifact = Arrays.stream(artifact.split("!")).skip(1).collect(Collectors.joining("!"));
            var packageName = forgelessArtifact.split("\\$")[0];
            var version = forgelessArtifact.split("\\$")[1];
            var partialUris = packageVersionUris.get(artifact);
            var urisMetadata = KnowledgeBaseConnector.kbDao.getCallablesMetadataByUri(forge, packageName, version, partialUris);
            if (urisMetadata != null) {
                metadataMap.putAll(urisMetadata);
            }
        }
        var json = new JSONObject();
        for (var entry : metadataMap.entrySet()) {
            var neededMetadata = new JSONObject();
            if (!allAttributes) {
                for (var attribute : entry.getValue().keySet()) {
                    if (attributes.contains(attribute)) {
                        neededMetadata.put(attribute, entry.getValue().get(attribute));
                    }
                }
            } else {
                neededMetadata = entry.getValue();
            }
            json.put(entry.getKey(), neededMetadata);
        }
        var result = json.toString();
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    public ResponseEntity<String> batchIngestArtifacts(JSONArray jsonArtifacts) {
        for (int i = 0; i < jsonArtifacts.length(); i++) {
            var json = jsonArtifacts.getJSONObject(i);
            var groupId = json.getString("groupId");
            var artifactId = json.getString("artifactId");
            var version = json.getString("version");
            var date = json.optLong("date", -1);
            var artifactRepository = json.optString("artifactRepository", null);
            LazyIngestionProvider.ingestArtifactIfNecessary(groupId + Constants.mvnCoordinateSeparator + artifactId, version, artifactRepository, date);
        }
        return new ResponseEntity<>("Ingested successfully", HttpStatus.OK);
    }
}
