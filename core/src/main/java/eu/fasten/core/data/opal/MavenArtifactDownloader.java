package eu.fasten.core.data.opal;

import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;

/**
 * A set of methods for downloading POM and JAR files given Maven coordinates.
 */
public class MavenArtifactDownloader {
    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactDownloader.class);
    private static final String[] packaging = {"jar", "war", "zip", "ear", "rar", "ejb", "par",
            "aar", "car", "nar", "kar"};
    private static final String[] defaultPackaging = {"zip", "aar", "tar.gz", "jar"};

    /**
     * Download a JAR file indicated by the provided Maven coordinate.
     *
     * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
     * @return A temporary file on the filesystem
     */
    public File downloadArtifact(final MavenCoordinate mavenCoordinate, String artifactRepo)
            throws MissingArtifactException {
        var found = false;
        Optional<File> jar = Optional.empty();
        var repos = mavenCoordinate.getMavenRepos();
        if (artifactRepo != null && !artifactRepo.isEmpty() && !artifactRepo.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
            repos.addFirst(artifactRepo);
        }
        for (int i = 0; i < repos.size(); i++) {

            long startTime = System.nanoTime();

            try {
                if (Arrays.asList(packaging).contains(mavenCoordinate.getPackaging())) {
                    found = true;
                    jar = httpGetFile(mavenCoordinate
                            .toProductUrl(repos.get(i), mavenCoordinate.getPackaging()));
                }
            } catch (MissingArtifactException e) {
                found = false;

                long duration = computeDurationInMs(startTime);
                logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + repos.get(i), e);
            }

            if (jar.isPresent()) {
                long duration = computeDurationInMs(startTime);
                logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + repos.get(i));
                return jar.get();
            } else if (found && i == repos.size() - 1) {
                throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + repos.get(i), null);
            } else if (found) {
                continue;
            }

            for (var s : defaultPackaging) {
                startTime = System.nanoTime();
                try {
                    found = true;
                    jar = httpGetFile(mavenCoordinate.toProductUrl(repos.get(i), s));
                } catch (MissingArtifactException e) {
                    found = false;

                    long duration = computeDurationInMs(startTime);
                    logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + repos.get(i), e);
                }

                if (jar.isPresent()) {long duration = computeDurationInMs(startTime);
                    logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + repos.get(i));
                    return jar.get();
                } else if (found && i == repos.size() - 1) {
                    throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + repos.get(i), null);
                } else if (found) {
                    break;
                }
            }
        }
        throw new MissingArtifactException(
                mavenCoordinate.toURL(mavenCoordinate.getMavenRepos().size() > 0
                        ? mavenCoordinate.getMavenRepos().get(0)
                        : "no repos specified") + " | "
                        + mavenCoordinate.getPackaging(), null);
    }

    /**
     * Utility function that stores the contents of GET request to a temporary file.
     */
    private static Optional<File> httpGetFile(final String url) throws MissingArtifactException {
        Path tempFile = null;
        try {
            final var packaging = url.substring(url.lastIndexOf("."));
            tempFile = Files.createTempFile("fasten", packaging);

            final InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            in.close();

            return Optional.of(new File(tempFile.toAbsolutePath().toString()));
        } catch (IOException e) {
            if (tempFile != null) {tempFile.toFile().delete();}
            throw new MissingArtifactException(e.getMessage(), e.getCause());
        }
    }

    private long computeDurationInMs(long startTime) {
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1000000; // Compute duration in ms.
    }
}