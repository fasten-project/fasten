package eu.fasten.analyzer.lapp.core;

import com.ibm.wala.types.Selector;

import java.util.jar.JarFile;

public interface AnalysisContext {

    ResolvedMethod makeResolved(String namespace, Selector symbol, JarFile artifact);

    UnresolvedMethod makeUnresolved(String namespace, Selector symbol);

}
