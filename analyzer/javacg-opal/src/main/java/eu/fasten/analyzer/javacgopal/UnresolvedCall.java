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

package eu.fasten.analyzer.javacgopal;

import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ReferenceType;

import eu.fasten.core.data.FastenURI;

public class UnresolvedCall extends UnresolvedMethodCall {

    public UnresolvedCall(Method caller, int pc, ReferenceType calleeClass, String calleeName, MethodDescriptor calleeDescriptor) {
        super(caller, pc, calleeClass, calleeName, calleeDescriptor);
    }

    /**
     * Converts unresolved calls to URIs.
     * @param unresolvedCall Calls without specified product name in their target. Source or callers of
     *                        such calls are org.opalj.br.Method presented in the under investigation artifact,
     *                        But targets or callees don't have a product.
     * @return List of two dimensional eu.fasten.core.data.FastenURIs[] which always the first dimension of the array is a
     * fully resolved method and the second one has an unknown product.
     */
    public static FastenURI[] toURICalls(UnresolvedMethodCall unresolvedCall) {

        FastenURI[] fastenURI = new FastenURI[2];

        var sourceURI = OPALMethodAnalyzer.toCanonicalFastenJavaURI(unresolvedCall.caller());

        if (sourceURI != null) {

            var targetURI = OPALMethodAnalyzer.toCanonicalFastenJavaURI(unresolvedCall.calleeClass(),
                unresolvedCall.calleeName(),
                unresolvedCall.calleeDescriptor());

            if (targetURI != null) {
                fastenURI[0] = sourceURI;
                fastenURI[1] = targetURI;
            }

        }
        return fastenURI;
    }

    /**
     * Converts unresolved calls to URIs.
     * @return List of two dimensional eu.fasten.core.data.FastenURIs[] which always the first dimension of the array is a
     * fully resolved method and the second one has an unknown product.
     */
    public FastenURI[] toURICalls() {
        return toURICalls(this);
    }

}
