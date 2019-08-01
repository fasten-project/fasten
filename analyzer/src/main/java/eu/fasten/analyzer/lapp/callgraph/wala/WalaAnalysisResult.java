package eu.fasten.analyzer.lapp.callgraph.wala;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class WalaAnalysisResult {

    public final CallGraph cg;
    public final IClassHierarchy extendedCha;


    public WalaAnalysisResult(CallGraph cg, IClassHierarchy extendedCha) {
        this.cg = cg;
        this.extendedCha = extendedCha;
    }
}
