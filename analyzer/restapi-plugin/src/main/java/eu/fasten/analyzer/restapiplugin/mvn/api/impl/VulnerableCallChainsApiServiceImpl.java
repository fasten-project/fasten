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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.VulnerableCallChainsApiService;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.vulchains.VulnerableCallChain;
import eu.fasten.core.vulchains.VulnerableCallChainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Set;


@Lazy
@Service
public class VulnerableCallChainsApiServiceImpl implements VulnerableCallChainsApiService {

    private static final Logger logger = LoggerFactory.getLogger(VulnerableCallChainsApiServiceImpl.class);

    private VulnerableCallChainRepository vulnerableCallChainRepository;

    public VulnerableCallChainsApiServiceImpl() {
        try {
            vulnerableCallChainRepository = new VulnerableCallChainRepository(KnowledgeBaseConnector.vulnerableCallChainsPath);
        } catch (Exception e) {
            logger.error("Error constructing Vulnerability Call Chain Repository", e);
            System.exit(1);
        }
    }

    /**
     * Helper method to convert set of vulnerable call chains to JSON formatted string response.
     * @param chains - a set of {@link VulnerableCallChain} objects to be serialized.
     * @return {@link String} formatted as JSON response.
     */
    private String VulnerableCallChainsToJSON(Set<VulnerableCallChain> chains) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.toJson(chains);
    }

    @Override
    public ResponseEntity<String> getChainsForPackage(String forge, String packageName, String packageVersion) {
        Set<VulnerableCallChain> chains = vulnerableCallChainRepository.getChainsForPackage(packageName, packageVersion);
        var result = VulnerableCallChainsToJSON(chains);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getChainsForModule(String forge, String packageName, String packageVersion, String rawPath) {
        FastenURI fastenUri = FastenURI.create(forge, packageName, packageVersion, rawPath);
        Set<VulnerableCallChain> chains = vulnerableCallChainRepository.getChainsForModule(fastenUri);
        var result = VulnerableCallChainsToJSON(chains);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getChainsForCallable(String forge, String packageName, String packageVersion, String rawPath) {
        FastenURI fastenUri = FastenURI.create(forge, packageName, packageVersion, rawPath);
        Set<VulnerableCallChain> chains = vulnerableCallChainRepository.getChainsForCallable(fastenUri);
        var result = VulnerableCallChainsToJSON(chains);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
