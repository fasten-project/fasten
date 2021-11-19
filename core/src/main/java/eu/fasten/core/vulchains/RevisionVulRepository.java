package eu.fasten.core.vulchains;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CallGraphUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevisionVulRepository {
    private static final Logger logger = LoggerFactory.getLogger(VulChainFindingInitiator.class);

    final private Revision rootRevision;
    final private String rootRevisionDirPath;

    private Revision vulDep;
    private String vulDepPath;

    private Set<Long> vulNodes;
    private String vulNodesPath;

    private Set<Revision> depSet;
    private String depSetPath;

    private BiMap<Long, FastenURI> uris;
    private String urisPath;

    private Set<List<Long>> vulChains;
    private String vulChainsPath;

    public RevisionVulRepository(String rootDir, Revision rootRevision) {
        this.rootRevision = rootRevision;

        rootRevisionDirPath = rootDir + "/" + rootRevision.toString();
        vulDepPath = rootRevisionDirPath + "/velDep";
        vulNodesPath = rootRevisionDirPath + "/vulNodes";
        depSetPath = rootRevisionDirPath + "/depSet";
        urisPath = rootRevisionDirPath + "/uris";
        vulChainsPath = rootRevisionDirPath + "/vulChains";

        vulNodes = new HashSet<>();
        depSet = new HashSet<>();
        vulDep = new Revision();
        uris = HashBiMap.create();
        vulChains = new HashSet<>();

        if (!new File(this.rootRevisionDirPath).exists()) {
            if (!new File(this.rootRevisionDirPath).mkdir()) {
                throw new RuntimeException("Failed to creat folder for revision!");
            }
        }
    }

    public void readVulDep() {
        vulDep = null;
    }

    public void readVulNodes() {
        vulNodes = null;
    }

    public void readDepset() {
        depSet = null;
    }

    public void readUris() {
        uris = null;
    }

    public void writeVulDep(Revision vulDep) {
        this.vulDep = vulDep;
    }

    public void readVulChains() {
        try (Stream<String> stream = Files.lines(Paths.get(vulChainsPath))) {
            stream.forEach(line -> vulChains.add(Arrays.stream(line.split(","))
                .map(Long::valueOf).collect(Collectors.toList())));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeVulNodes(Set<Long> vulNodes) {
        this.vulNodes = vulNodes;
    }

    public void writeDepset(Set<Revision> depSet) {
        this.depSet = depSet;
    }

    public void writeUris(BiMap<Long, FastenURI> uris) {
        this.uris = uris;
    }

    public void writeVulChains(Set<List<Long>> vulChains) {
        this.vulChains = vulChains;
        StringBuilder content = new StringBuilder();
        String delimiter = "";
        for (final var vulPath : vulChains) {
            content.append(delimiter);
            delimiter = "\n";
            content.append(
                vulChains.stream().map(Object::toString).collect(Collectors.joining(",")));
        }

        try {
            CallGraphUtils.writeToFile(this.vulDepPath, content.toString());
        } catch (IOException e) {
            logger.error("Error happened while writing vul paths of package" +
                rootRevision.toString(), e);
        }
    }

    public Revision getRootRevision() {
        return rootRevision;
    }

    public String getRootRevisionDirPath() {
        return rootRevisionDirPath;
    }

    public Revision getVulDep() {
        return vulDep;
    }

    public String getVulDepPath() {
        return vulDepPath;
    }

    public Set<Long> getVulNodes() {
        return vulNodes;
    }

    public String getVulNodesPath() {
        return vulNodesPath;
    }

    public Set<Revision> getDepSet() {
        return depSet;
    }

    public String getDepSetPath() {
        return depSetPath;
    }

    public BiMap<Long, FastenURI> getUris() {
        return uris;
    }

    public String getUrisPath() {
        return urisPath;
    }

    public Set<List<Long>> getVulChains() {
        return vulChains;
    }

    public String getVulChainsPath() {
        return vulChainsPath;
    }

    public void setVulDep(Revision vulDep) {
        this.vulDep = vulDep;
    }

    public void setVulDepPath(String vulDepPath) {
        this.vulDepPath = vulDepPath;
    }

    public void setVulNodes(Set<Long> vulNodes) {
        this.vulNodes = vulNodes;
    }

    public void setVulNodesPath(String vulNodesPath) {
        this.vulNodesPath = vulNodesPath;
    }

    public void setDepSet(Set<Revision> depSet) {
        this.depSet = depSet;
    }

    public void setDepSetPath(String depSetPath) {
        this.depSetPath = depSetPath;
    }

    public void setUris(BiMap<Long, FastenURI> uris) {
        this.uris = uris;
    }

    public void setUrisPath(String urisPath) {
        this.urisPath = urisPath;
    }

    public void setVulChains(Set<List<Long>> vulChains) {
        this.vulChains = vulChains;
    }

    public void setVulChainsPath(String vulChainsPath) {
        this.vulChainsPath = vulChainsPath;
    }
}
