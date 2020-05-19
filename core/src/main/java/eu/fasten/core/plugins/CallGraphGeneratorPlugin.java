package eu.fasten.core.plugins;

/**
 * CallGraphGeneratorPlugin is a marker interface that indicates that
 * the class implementing it is responsible for producing RevisionCallGraph-s.
 * This approach allows to differentiate plugins output of which should be
 * stored in a directory.
 */
public interface CallGraphGeneratorPlugin {
}
