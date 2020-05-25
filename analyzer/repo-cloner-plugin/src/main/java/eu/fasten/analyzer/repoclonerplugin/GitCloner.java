package eu.fasten.analyzer.repoclonerplugin;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class GitCloner {

    private final String baseDir;

    public GitCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    public String cloneRepo(String artifact, String repoUrl) throws GitAPIException, IOException {
        if (!repoUrl.endsWith(".git")) {
            repoUrl += ".git";
        }
        var dir = this.getDirectoryFromHierarchy(artifact);
        if (dir.exists()) {
            Git.open(dir).pull().call();
        } else {
            Git.cloneRepository().setURI(repoUrl).setDirectory(dir).call();
        }
        return dir.getAbsolutePath();
    }

    public File getDirectoryFromHierarchy(String artifact) {
        var dir = Paths.get(this.baseDir, "mvn", String.valueOf(artifact.charAt(0)), artifact);
        return new File(dir.toString());
    }
}
