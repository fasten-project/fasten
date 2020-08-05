package eu.fasten.analyzer.repoclonerplugin.utils;

import org.apache.commons.lang3.StringUtils;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgRepoFacade;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.util.CancelledException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class HgCloner {

    private final String baseDir;

    public HgCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    public String cloneRepo(String repoUrl)
            throws MalformedURLException, HgException, CancelledException {
        var urlParts = repoUrl.split("/");
        if (urlParts[urlParts.length - 1].contains("?at=")) {
            urlParts = Arrays.stream(urlParts)
                    .filter(x -> !x.contains("?at=")).toArray(String[]::new);
            repoUrl = String.join("/", urlParts);
        }
        if (repoUrl.endsWith("/")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 1);
        }
        if (repoUrl.endsWith("/src")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 4);
        }
        urlParts = Arrays.stream(repoUrl.split("/"))
                .filter(x -> !StringUtils.isBlank(x)).toArray(String[]::new);
        var repoOwner = urlParts[urlParts.length - 2];
        var repoName = urlParts[urlParts.length - 1];
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(repoOwner, repoName);
        var hgRepo = new HgRepoFacade();
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
