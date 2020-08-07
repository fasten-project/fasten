package eu.fasten.analyzer.repoclonerplugin.utils;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnCloner {

    private final String baseDir;

    public SvnCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Clones SVN Repository from repository URL.
     *
     * @param repoUrl   URL where repository is located
     * @param repoName  Name of the repository
     * @param repoOwner Owner of the repository
     * @return Path to where repository has been cloned
     * @throws SVNException if there was an error cloning the repository
     */
    public String cloneRepo(String repoUrl, String repoName, String repoOwner) throws SVNException {
        var repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repoUrl));
        var updateClient = SVNClientManager.newInstance().getUpdateClient();
        updateClient.setIgnoreExternals(false);
        var url = SVNURL.parseURIEncoded(repoUrl);
        var revision = SVNRevision.create(repository.getLatestRevision());
        var depth = SVNDepth.fromRecurse(true);
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(repoOwner, repoName);
        updateClient.doCheckout(url, dir, revision, revision, depth, true);
        return dir.getAbsolutePath();
    }
}
