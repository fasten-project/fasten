package eu.fasten.analyzer.repoclonerplugin;

import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class GitCloner {

    private final String baseDir;

    public GitCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Clones git repository from provided URL into hierarchical directory structure.
     *
     * @param artifact Name of the repository
     * @param repoUrl  URL at which the repository is located
     * @return Path to directory to which the repository was cloned
     * @throws GitAPIException if there was an error when cloning repository
     * @throws IOException     if could not create a directory for repository
     */
    public String cloneRepo(String artifact, String repoUrl) throws GitAPIException, IOException {
        if (repoUrl.endsWith("/")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 1);
        }
        if (!repoUrl.endsWith(".git")) {
            repoUrl += ".git";
        }
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(artifact);
        if (dir.exists()) {
            Git.open(dir).pull().call();
        } else {
            Git.cloneRepository().setURI(repoUrl).setDirectory(dir).call();
        }
        return dir.getAbsolutePath();
    }
}
