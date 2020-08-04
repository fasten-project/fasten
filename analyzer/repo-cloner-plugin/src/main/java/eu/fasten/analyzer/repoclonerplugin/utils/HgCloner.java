package eu.fasten.analyzer.repoclonerplugin.utils;

import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgRepoFacade;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.util.CancelledException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class HgCloner {

    private final String baseDir;

    public HgCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    public String cloneRepo(String repoUrl) throws MalformedURLException, HgException, CancelledException {
        var hgRepo = new HgRepoFacade();
        hgRepo.createCloneCommand()
                .destination(new File("/home/mihhail/work/test"))
                .source(new HgLookup().detect(new URL(repoUrl)))
                .execute();
        // TODO: Fix cloning; currently cloned repo is empty (only .hg folder)
        return "Cloned";
    }
}
