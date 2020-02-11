package eu.fasten.analyzer.javacgwala.data;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.JarFileEntry;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;

import java.io.IOException;
import java.util.Objects;
import java.util.jar.JarFile;

public class ArtifactResolver {

    private final IClassHierarchy cha;

    public ArtifactResolver(IClassHierarchy cha) {
        this.cha = cha;
    }

    /**
     * Get a jar file containing a given method.
     *
     * @param n Method reference
     * @return Jar file
     */
    public JarFile findJarFileUsingMethod(MethodReference n) {
        IClass klass = cha.lookupClass(n.getDeclaringClass());
        return classToJarFile(klass);
    }

    private JarFile classToJarFile(IClass klass) {
        Objects.requireNonNull(klass);

        if (klass instanceof ArrayClass) {
            ArrayClass arrayClass = (ArrayClass) klass;
            IClass innerClass = arrayClass.getElementClass();

            if (innerClass == null) {
                // getElementClass returns null for primitive types
                if (klass.getReference().getArrayElementType().isPrimitiveType()) {
                    try {
                        return new JarFile("rt.jar");
                    } catch (IOException e) {
                        return null;
                    }
                }

                return null;
            }
        }

        try {
            ShrikeClass shrikeKlass = (ShrikeClass) klass;
            JarFileEntry moduleEntry = (JarFileEntry) shrikeKlass.getModuleEntry();

            return moduleEntry.getJarFile();
        } catch (ClassCastException e) {
            return null;
        }
    }
}
