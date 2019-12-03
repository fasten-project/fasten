package eu.fasten.analyzer.javacgopal;

import org.opalj.br.ClassHierarchy;

import java.util.List;

public class CallGraph {
    /**
     * Calls that their sources and targets are fully resolved.
     */
    private List<ResolvedCall> resolvedCalls;
    /**
     * ClassHierarchy in OPAL format.
     */
    private ClassHierarchy classHierarchy;



}
