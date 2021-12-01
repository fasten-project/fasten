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

public class VulRepository {

    private final String rootDir;

    public VulRepository(String rootDir) throws FileNotFoundException {
        if (new File(rootDir).exists()) {
            this.rootDir = rootDir;
        } else {
            throw new FileNotFoundException("Could not find the root directory!");
        }
    }

    public Set<VulnerableChain> getChainsForPackage(final String packag, final String version) {
        final var vulFile = getFilePath(packag, version);
        String reader;
        try {
            reader = Files.readString(Paths.get(vulFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Type setType = new TypeToken<HashSet<VulnerableChain>>(){}.getType();

        return JsonUtils.fromJson(reader, setType);

    }

    public Set<VulnerableChain> getChainsForModule(final FastenURI module) {
        final var packgVul = getChainsForPackage(module.getProduct(), module.getVersion());
        final var result = new HashSet<VulnerableChain>();
        for (final var vulnerableChain : packgVul) {
            for (final var uriInChain : vulnerableChain.chain) {
                if (uriInChain.toString().startsWith(module.toString())) {
                    result.add(vulnerableChain);
                }
            }
        }
        return result;
    }

    public Set<VulnerableChain> getChainsForCallable(final FastenURI callable) {
        final var packgVul = getChainsForPackage(callable.getProduct(), callable.getVersion());
        final var result = new HashSet<VulnerableChain>();
        for (final var vulnerableChain : packgVul) {
            for (final var uriInChain : vulnerableChain.chain) {
                if (uriInChain.equals(callable)) {
                    result.add(vulnerableChain);
                }
            }
        }
        return result;
    }


    public void store(final String packag, final String version,
                      final Set<VulnerableChain> vulns) {
        final var vulFileFile = new File(getFilePath(packag, version));
        final var jsonString = JsonUtils.toJson(vulns);
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
