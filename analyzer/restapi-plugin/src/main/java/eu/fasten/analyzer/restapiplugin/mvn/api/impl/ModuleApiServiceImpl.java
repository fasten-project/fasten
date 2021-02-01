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
import eu.fasten.analyzer.restapiplugin.mvn.api.ModuleApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ModuleApiServiceImpl implements ModuleApiService {

    @Override
    public ResponseEntity<String> getPackageModules(String package_name,
                                                    String package_version,
                                                    int offset,
                                                    int limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageModules(
                package_name, package_version, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getModuleMetadata(String package_name,
                                                    String package_version,
                                                    String module_namespace) {
        String result = KnowledgeBaseConnector.kbDao.getModuleMetadata(
                package_name, package_version, module_namespace);
        if (result == null) {
            return new ResponseEntity<>("Module not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getModuleFiles(String package_name,
                                                 String package_version,
                                                 String module_namespace,
                                                 int offset,
                                                 int limit) {
        String result = KnowledgeBaseConnector.kbDao.getModuleFiles(
                package_name, package_version, module_namespace, offset, limit);
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getModuleCallables(String package_name,
                                                 String package_version,
                                                 String module_namespace,
                                                 int offset,
                                                 int limit) {
        String result = KnowledgeBaseConnector.kbDao.getModuleCallables(
                package_name, package_version, module_namespace, offset, limit);
        if (result == null) {
            return new ResponseEntity<>("Module not found", HttpStatus.NOT_FOUND);
        }
        result = result.replace("\\/", "/");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
