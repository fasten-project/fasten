package eu.fasten.analyzer.lapp.core;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.fasten.analyzer.lapp.call.Call;
import eu.fasten.analyzer.lapp.call.ChaEdge;
import eu.fasten.analyzer.lapp.callgraph.ArtifactRecord;

public class LappPackage {
    public final Set<ArtifactRecord> artifacts = new HashSet<>();

    public final Set<ResolvedMethod> methods = new HashSet<>();
    public final Set<Call> resolvedCalls = new HashSet<>();
    public final Set<Call> unresolvedCalls = new HashSet<>();

    public final Set<ChaEdge> cha = new HashSet<>();
    public final Set<ChaEdge> unresolvedCha = new HashSet<>();

    public final Map<String, String> metadata = new HashMap<>();

    public void addResolvedMethod(ResolvedMethod resolvedMethod) {
        methods.add(resolvedMethod);
    }

    public boolean addCall(Method source, Method target, Call.CallType type) {

        if (target instanceof ResolvedMethod
                && source instanceof ResolvedMethod) {
            return addResolvedCall((ResolvedMethod) source, (ResolvedMethod) target, type);
        }

        return addUnresolvedCall(source, target, type);
    }

    private boolean addUnresolvedCall(Method source, Method target, Call.CallType type) {
        Call call = new Call(source, target, type);

        return unresolvedCalls.add(call);
    }

    private boolean addResolvedCall(ResolvedMethod source, ResolvedMethod target, Call.CallType type) {
        Call call = new Call(source, target, type);

        return resolvedCalls.add(call);
    }

    public boolean addChaEdge(Method related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {
        if (related instanceof ResolvedMethod) {
            return addResolvedChaEdge((ResolvedMethod) related, (ResolvedMethod) subject, type);
        }

        return addUnresolvedChaEdge(related, subject, type);

    }

    public boolean addResolvedChaEdge(ResolvedMethod related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {
        return cha.add(new ChaEdge(related, subject, type));
    }

    public boolean addUnresolvedChaEdge(Method related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {
        return unresolvedCha.add(new ChaEdge(related, subject, type));
    }

}
