package eu.fasten.analyzer.javacgopal.data.callgraph;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.net.URISyntaxException;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;

public class ProposalRevisionCallGraph extends RevisionCallGraph {

    private Map<FastenURI,Type> classHierarchy;

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public void setClassHierarchy(Map<FastenURI, Type> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    /**
     * Type can be a class or interface that inherits (implements) from others or implements methods.
     */
    public static class Type{
        //Methods that this type implements
        private List<FastenURI> methods;
        //Classes that this type inherits from in the order of instantiation.
        private LinkedList<FastenURI> superClasses;
        //Interfaces that this type or its super classes implement.
        private List<FastenURI> superInterfaces;

        public List<FastenURI> getMethods() {
            return methods;
        }

        public LinkedList<FastenURI> getSuperClasses() {
            return superClasses;
        }

        public List<FastenURI> getSuperInterfaces() {
            return superInterfaces;
        }

        public void setMethods(List<FastenURI> methods) {
            this.methods = methods;
        }

        public void setSuperClasses(LinkedList<FastenURI> superClasses) {
            this.superClasses = superClasses;
        }

        public void setSuperInterfaces(List<FastenURI> superInterfaces) {
            this.superInterfaces = superInterfaces;
        }

        public Type(List<FastenURI> methods, LinkedList<FastenURI> superClasses, List<FastenURI> superInterfaces) {
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    public ProposalRevisionCallGraph(String forge, String product, String version, long timestamp, List<List<Dependency>> depset, ArrayList<FastenURI[]> graph, Map<FastenURI,Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public ProposalRevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
        super(json, ignoreConstraints);
    }
}
