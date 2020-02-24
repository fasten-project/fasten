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

package eu.fasten.analyzer.javacgopal.scalawrapper;

import org.opalj.br.Method;

import scala.Function0;
import scala.collection.Iterable;
import scala.collection.Map;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.AbstractFunction2;

/**
 * A wrapper class that wraps java code to be passed to scala.
 * Especially in case of functional programing.
 */
public final class JavaToScalaConverter {

    /**
     * Imitate a scala function0 in case of passing entrypoints as an scala function.
     * @param defaultString Scala Iterable of methods.
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

    public static Function0<Iterable<Method>> asScalaFunction0EntryPionts(final Iterable<Method> entryPoints) {
        return new AbstractFunction0<>() {

            @Override
            public Iterable<Method> apply() {
                return entryPoints;
            }
        };
    }
    /**
     * Imitates a scala function1 in case of Lambda in java to be passed to scala.
     * @param lambdaFunction A java Lambda in order to do things on scala.
     * @return Execution of java Lambda as scala function1.
     */
    public static AbstractFunction1 asScalaFunction1(final ScalaFunction1 lambdaFunction) {
        return new AbstractFunction1() {

            @Override
            public Object apply(Object v1) {
                return lambdaFunction.execute(v1);
            }
        };
    }

    /**
     * Imitates a scala function2 in case of java method to be passed to scala.
     * @param javaFunction A java function in order to do things on scala.
     * @return Execution of java function as scala function2.
     */
    public static AbstractFunction2<Method, Map<Object, Iterable<Method>>, Object> asScalaFunction2(final ScalaFunction2 javaFunction) {
        return new AbstractFunction2<>() {

            @Override
            public Object apply(Method v1, Map<Object, Iterable<Method>> v2) {
                return javaFunction.execute(v1, v2);
            }
        };
    }

}
