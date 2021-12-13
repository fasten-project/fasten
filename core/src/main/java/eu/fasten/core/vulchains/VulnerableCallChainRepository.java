/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.vulchains;

import com.google.common.reflect.TypeToken;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.FileUtils;

public class VulnerableCallChainRepository {
    int MAXSETSIZE = 5;

    private final String rootDir;

    public VulnerableCallChainRepository(String rootDir) throws FileNotFoundException {
        if (new File(rootDir).exists()) {
            this.rootDir = rootDir;
        } else {
            throw new FileNotFoundException("Could not find the root directory!");
        }
    }

    public Set<VulnerableCallChain> getChainsForPackage(final String packag, final String version) {
        final var vulFile = getFilePath(packag, version);
        String reader;
        try {
            reader = Files.readString(Paths.get(vulFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Type setType = new TypeToken<HashSet<VulnerableCallChain>>(){}.getType();
        Set<VulnerableCallChain> fullSet = VulnerableCallChainJsonUtils.fromJson(reader, setType);
        if (fullSet.size()>MAXSETSIZE) {
            Set<VulnerableCallChain> truncatedSet = new HashSet<>();
            for (VulnerableCallChain vulRepository : fullSet) {
                truncatedSet.add(vulRepository);
                if (truncatedSet.size() == MAXSETSIZE) {
                    break;
                }
            }
            return truncatedSet;
        }
        return fullSet;

    }

    public Set<VulnerableCallChain> getChainsForModule(final FastenURI module) {
        final var packgVul = getChainsForPackage(module.getProduct(), module.getVersion());
        final var result = new HashSet<VulnerableCallChain>();
        for (final var vulnerableChain : packgVul) {
            if (result.size() == MAXSETSIZE) {
                break;
            }
            for (final var uriInChain : vulnerableChain.chain) {
                if (uriInChain.toString().startsWith(module.toString())) {
                    result.add(vulnerableChain);
                    break;
                }
            }
        }
        return result;
    }

    public Set<VulnerableCallChain> getChainsForCallable(final FastenURI callable) {
        final var packgVul = getChainsForPackage(callable.getProduct(), callable.getVersion());
        final var result = new HashSet<VulnerableCallChain>();
        for (final var vulnerableChain : packgVul) {
            if (result.size() == MAXSETSIZE) {
                break;
            }
            for (final var uriInChain : vulnerableChain.chain) {
                if (uriInChain.equals(callable)) {
                    result.add(vulnerableChain);
                    break;
                }
            }
        }
        return result;
    }


    public void store(final String packag, final String version,
                      final Set<VulnerableCallChain> vulns) {
        final var vulFileFile = new File(getFilePath(packag, version));
        final var jsonString = VulnerableCallChainJsonUtils.toJson(vulns);
        writeContent(jsonString, vulFileFile);
    }

    private void writeContent(final String content, final File vulNodesFile) {
        try {
            FileUtils.write(vulNodesFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFilePath(String packag, String version) {
        return this.rootDir + "/" + packag + ":" + version + ".json";
    }
}
