package eu.fasten.analyzer.javacgwala.data;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactResolverTest {

    private static CallGraph graph;
    private static File jar;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        jar = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile());

        graph = CallGraphConstructor.generateCallGraph(jar.getAbsolutePath());
    }

    @Test
    void findJarFileUsingMethod() throws IOException {
        ArtifactResolver artifactResolver = new ArtifactResolver(graph.getClassHierarchy());
        JarFile jarFile = new JarFile(jar);

        CGNode correctClassLoaderNode = null;

        for (CGNode node : graph) {
            MethodReference nodeReference = node.getMethod().getReference();

            if (!node.getMethod()
                    .getDeclaringClass()
                    .getClassLoader()
                    .getReference()
                    .equals(ClassLoaderReference.Application)) {
                continue;
            }

            correctClassLoaderNode = node;
            break;
        }

        assertNotNull(correctClassLoaderNode);
        assertEquals(jarFile.getName(), artifactResolver
                .findJarFileUsingMethod(correctClassLoaderNode.getMethod().getReference()).getName());
    }
}