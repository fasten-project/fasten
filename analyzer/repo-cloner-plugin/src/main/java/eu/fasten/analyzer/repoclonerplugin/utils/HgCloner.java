package eu.fasten.analyzer.repoclonerplugin.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgRepoFacade;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.util.CancelledException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class HgCloner {

    private final String baseDir;

    public HgCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    public String cloneRepo(String repoUrl, String repoName, String repoOwner)
            throws MalformedURLException, HgException, CancelledException {
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(repoOwner, repoName);
        var hgRepo = new HgRepoFacade();
        if (dir.exists()) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                return dir.getAbsolutePath();
            }
        }
        hgRepo.createCloneCommand()
                .source(new HgLookup().detect(new URL(repoUrl)))
                .destination(dir)
                .execute();
        hgRepo.initFrom(dir);
        var lastRevision = hgRepo.getRepository().getChangelog().getLastRevision();
        hgRepo.createCheckoutCommand()
                .clean(true)
                .changeset(lastRevision)
                .execute();
        return dir.getAbsolutePath();
    }
}
