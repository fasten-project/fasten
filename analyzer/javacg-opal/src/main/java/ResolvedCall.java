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

import org.opalj.br.Method;

import java.util.List;

/**
 * Calls that both source and target are fully known at the moment.
 */
public class ResolvedCall {
    /**
     * Each resolved call consists of one source and one or more targets.
     * e.g. if method A calls B,C and D.
     * Then we have Souce: A, target B,C,D.
     */
    private Method source;
    private List<Method> target;

    public ResolvedCall(Method source, List<Method> target) {
        this.source = source;
        this.target = target;
    }

    public void setSource(Method source) {
        this.source = source;
    }

    public void setTarget(List<Method> target) {
        this.target = target;
    }

    public Method getSource() { return source; }

    public List<Method> getTarget() { return target; }
}
