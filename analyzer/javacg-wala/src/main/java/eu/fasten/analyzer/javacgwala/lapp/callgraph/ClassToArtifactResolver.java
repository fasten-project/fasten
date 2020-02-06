/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package eu.fasten.analyzer.javacgwala.lapp.callgraph;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.JarFileEntry;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout.ArtifactFolderLayout;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClassToArtifactResolver implements ClassArtifactResolver {
    private static Logger logger = LoggerFactory.getLogger(ClassArtifactResolver.class);

    private final ArtifactFolderLayout transformer;
    private IClassHierarchy cha;

    private HashMap<IClass, ArtifactRecord> classArtifactRecordCache = new HashMap<>();

    public ClassToArtifactResolver(IClassHierarchy cha, ArtifactFolderLayout transformer) {
        this.cha = cha;
        this.transformer = transformer;
    }

    /**
     * Get a jar file containing a given method.
     *
     * @param n - method reference
     * @return - jar file
     */
    public JarFile findJarFileUsingMethod(MethodReference n) {
        IClass klass = cha.lookupClass(n.getDeclaringClass());
        JarFile jarFile = classToJarFile(klass);
        return jarFile;
    }

    @Override
    public ArtifactRecord artifactRecordFromMethodReference(MethodReference n) {
        IClass klass = cha.lookupClass(n.getDeclaringClass());

        if (klass == null) {
            // Try harder
            TypeReference t = TypeReference
                    .findOrCreate(cha.getLoader(ClassLoaderReference.Application).getReference(),
                            n.getDeclaringClass().getName());
            klass = cha.lookupClass(t);
        }

        if (klass == null) {
            logger.warn("Couldn't find class for {}", n);
            return new ArtifactRecord("unknown", n.getDeclaringClass().toString(), "unknown");
        }


        return artifactRecordFromClass(klass);
    }

    @Override
    public ArtifactRecord artifactRecordFromClass(IClass klass) {
        Objects.requireNonNull(klass);

        if (classArtifactRecordCache.containsKey(klass)) {
            return classArtifactRecordCache.get(klass);
        }

        JarFile jarFile = classToJarFile(klass);

        if (jarFile == null) {
            return new ArtifactRecord("unknown", "unknown", "unknown");
        }

        ArtifactRecord artifactRecord = transformer.artifactRecordFromJarFile(jarFile);

        // Store in cache
        classArtifactRecordCache.put(klass, artifactRecord);

        return artifactRecord;
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

            JarFile jf = moduleEntry.getJarFile();

            return jf;
        } catch (ClassCastException e) {
            return null;
        }
    }

}
