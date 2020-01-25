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

package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.core.data.FastenURI;

import java.util.List;
import java.util.ArrayList;

/**
 * Calls that both source and target are fully known at the moment.
 */
public class ResolvedCall {
    /**
     * Each resolved call consists of one source and one or more targets.
     * e.g. if method A calls B,C and D.
     * Then we have Souce: A, target B,C,D.
     */
    private org.opalj.br.Method source;
    private List<org.opalj.br.Method> targets;

    public ResolvedCall(org.opalj.br.Method source, List<org.opalj.br.Method> targets) {
        this.source = source;
        this.targets = targets;
    }


    public void clearTargets(){this.targets.clear();}

    public void setSource(org.opalj.br.Method source) {
        this.source = source;
    }

    public void setTargets(List<org.opalj.br.Method> targets) {
        this.targets = targets;
    }

    public org.opalj.br.Method getSource() { return source; }

    public List<org.opalj.br.Method> getTargets() { return targets; }

    /**
     * Converts resolved calls to URI pairs. Since there each source in resolvedCall may have multiple targets the result is a list of calls.
     *
     * @param resolvedCall All callers and callees are org.opalj.br.Method.
     *
     * @return List of two dimensional eu.fasten.core.data.FastenURIs[] which both dimensions of the array are
     * fully resolved methods.
     */
    public static ArrayList<FastenURI[]> toURICalls(ResolvedCall resolvedCall) {

        var resolvedCallURIs = new ArrayList<FastenURI[]>();

        var sourceURI = Method.toCanonicalSchemelessURI(
            null,
            resolvedCall.getSource().declaringClassFile().thisType(),
            resolvedCall.getSource().name(),
            resolvedCall.getSource().descriptor());

        if ( sourceURI != null) {
            for (org.opalj.br.Method target : resolvedCall.getTargets()) {

                FastenURI[] fastenURI = new FastenURI[2];
                var targetURI =  Method.toCanonicalSchemelessURI(
                    null,
                    target.declaringClassFile().thisType(),
                    target.name(),
                    target.descriptor());

                if ( targetURI != null ) {
                    fastenURI[0] = sourceURI;
                    fastenURI[1] = targetURI;
                    resolvedCallURIs.add(fastenURI);
                }

            }
        }
        return resolvedCallURIs;
    }

    /**
     * Converts resolved calls to URI pairs.
     *
     * @return List of two dimensional eu.fasten.core.data.FastenURI[] which both dimensions of the array are
     * fully resolved methods.
     */
    public ArrayList<FastenURI[]> toURICalls() {
        return toURICalls(this);
    }

}
