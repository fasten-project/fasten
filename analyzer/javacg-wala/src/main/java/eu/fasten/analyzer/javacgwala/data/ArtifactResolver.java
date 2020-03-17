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

    /**
     * Construct artifact resolver.
     *
     * @param cha Class hierarchy
     */
    public ArtifactResolver(final IClassHierarchy cha) {
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

    /**
     * Get a jar file containing given class.
     *
     * @param klass Class
     * @return Jar File
     */
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

        if (klass instanceof ShrikeClass) {
            var shrikeKlass = (ShrikeClass) klass;
            var moduleEntry = shrikeKlass.getModuleEntry();

            return ((JarFileEntry) moduleEntry).getJarFile();

        }

        return null;
    }
}
