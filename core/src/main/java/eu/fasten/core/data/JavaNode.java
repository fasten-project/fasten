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

package eu.fasten.core.data;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

public class JavaNode extends Node {

    /**
     * Method signature.
     */
    final private String signature;

    /**
     * Creates {@link JavaNode} from a FastenURI and metadata.
     *
     * @param uri      FastenURI corresponding to this JavaNode
     * @param metadata metadata associated with this JavaNode
     */
    public JavaNode(final FastenURI uri, final Map<String, Object> metadata) {
        super(uri, metadata);
        this.signature = StringUtils.substringAfter(FastenJavaURI.create(uri.toString()).decanonicalize().getEntity(), ".");
    }

    /**
     * Get method signature.
     *
     * @return method signature
     */
    public String getSignature() {
        return signature;
    }

    /**
     * Extract a class name from the FastenURI.
     *
     * @return class name
     */
    public String getClassName() {
        return getEntity().substring(0, getEntity().indexOf("."));
    }

    /**
     * Extract a method name from the FastenURI.
     *
     * @return method name
     */
    public String getMethodName() {
        return StringUtils.substringBetween(getEntity(), getClassName() + ".", "(");
    }

    /**
     * Changes the class and method names in the FastenURI.
     *
     * @param className  new class name
     * @param methodName new method name
     * @return FastenURI with new class and method names
     */
    public FastenURI changeName(final String className, final String methodName) {
        final var uri = this.getUri().toString().replace("/" + getClassName() + ".", "/" + className + ".");
        return FastenURI.create(uri.replace("." + getMethodName() + "(", "." + methodName + "("));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Node node = (Node) o;

        return Objects.equals(uri, node.uri);
    }

    @Override
    public int hashCode() {
        return uri != null ? uri.hashCode() : 0;
    }
  
     /**
     * Change method signature.
     *
     * @param methodName new method name
     * @return method signature with changed name
     */
    public String changeSignature(final String methodName) {
        return signature.replace(getMethodName() + "(", methodName + "(");
    }
}
