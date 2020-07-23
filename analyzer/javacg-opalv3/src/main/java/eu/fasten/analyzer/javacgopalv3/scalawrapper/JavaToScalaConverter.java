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

package eu.fasten.analyzer.javacgopalv3.scalawrapper;

import org.opalj.br.ObjectType;
import scala.Function0;
import scala.Function1;
import scala.runtime.AbstractFunction0;

/**
 * A wrapper class that wraps java code to be passed to scala. Especially in case of functional
 * programing.
 */
public final class JavaToScalaConverter {

    /**
     * Imitate a scala function0 in case of passing Optional for String as an scala function.
     *
     * @param defaultString String.
     * @return An scala function including the results.
     */
    public static Function0<String> asScalaFunction0OptionString(final String defaultString) {
        return new AbstractFunction0<>() {

            @Override
            public String apply() {
                return defaultString;
            }
        };
    }

    /**
     * Imitate a scala function0 in case of passing Optional for Integer as an scala function.
     *
     * @param defaultString Integer.
     * @return An scala function including the results.
     */
    public static Function0<Integer> asScalaFunction0OptionInteger(final Integer defaultString) {
        return new AbstractFunction0<>() {

            @Override
            public Integer apply() {
                return defaultString;
            }
        };
    }

    /**
     * Imitates a scala function1 in case of Lambda in java to be passed to scala.
     *
     * @param lambdaFunction A java Lambda in order to do things on scala.
     * @return Execution of java Lambda as scala function1.
     */
    public static Function1<ObjectType, Object> asScalaFunction1(
            final ScalaFunction1 lambdaFunction) {
        return (Function1) v1 -> lambdaFunction.execute(v1);
    }
}
