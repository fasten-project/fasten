package eu.fasten.analyzer.repoclonerplugin.utils;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import java.io.File;

public class SvnCloner {

    private final String baseDir;

    public SvnCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    public String cloneRepo(String repoUrl) throws SVNException {
        var repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repoUrl));
        var latestRevision = repository.getLatestRevision();
        var updateClient = SVNClientManager.newInstance().getUpdateClient();
        updateClient.setIgnoreExternals(false);
        updateClient.doCheckout(SVNURL.parseURIEncoded(repoUrl), new File(baseDir), SVNRevision.create(latestRevision) , SVNRevision.create(latestRevision), SVNDepth.fromRecurse(true), true);
        // TODO: Fix SVN Repositories cloning. Currently it gives warnings and doesn't halt
        return baseDir;
    }
}
