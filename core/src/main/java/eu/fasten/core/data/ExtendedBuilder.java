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

/**
 * Builder for {@link ExtendedRevisionCallGraph}.
 */
public abstract class ExtendedBuilder<A> {

    protected String forge;
    protected String product;
    protected String version;
    protected String cgGenerator;
    protected long timestamp;
    protected A classHierarchy;
    protected Graph graph;
    protected int nodeCount;

    public ExtendedBuilder() {
    }

    public String getForge() {
        return forge;
    }

    public String getProduct() {
        return product;
    }

    public String getVersion() {
        return version;
    }

    public String getCgGenerator() {
        return cgGenerator;
    }

    public long getTimeStamp() {
        return timestamp;
    }

    public A getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public ExtendedBuilder<A> nodeCount(final int nodeCount) {
        this.nodeCount = nodeCount;
        return this;
    }

    public ExtendedBuilder<A> forge(final String forge) {
        this.forge = forge;
        return this;
    }

    public ExtendedBuilder<A> product(final String product) {
        this.product = product;
        return this;
    }

    public ExtendedBuilder<A> version(final String version) {
        this.version = version;
        return this;
    }

    public ExtendedBuilder<A> cgGenerator(final String cgGenerator) {
        this.cgGenerator = cgGenerator;
        return this;
    }

    public ExtendedBuilder<A> timestamp(final long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public ExtendedBuilder<A> graph(final Graph graph) {
        this.graph = graph;
        return this;
    }

    public ExtendedBuilder<A> classHierarchy(final A cha) {
        this.classHierarchy = cha;
        return this;
    }

    public abstract ExtendedRevisionCallGraph<A> build();
}
