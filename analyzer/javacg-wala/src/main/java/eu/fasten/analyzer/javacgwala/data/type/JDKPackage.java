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


package eu.fasten.analyzer.javacgwala.data.type;

import java.io.Serializable;
import java.util.Objects;

public final class JDKPackage implements Serializable, Namespace {

    public final String version = Runtime.class.getPackage().getImplementationVersion();
    public final String vendor = Runtime.class.getPackage().getImplementationVendor();

    private JDKPackage() {
    }

    public static JDKPackage getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;
        // null check
        if (o == null)
            return false;
        // type check and cast
        if (getClass() != o.getClass())
            return false;
        JDKPackage jdk = (JDKPackage) o;
        return
                Objects.equals(this.vendor, jdk.vendor) &&
                        Objects.equals(this.version, jdk.version);
    }

    @Override
    public String[] getSegments() {
        return new String[]{this.version, this.vendor};
    }

    @Override
    public String getNamespaceDelim() {
        return ".";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.version, this.vendor);
    }

    protected Object readResolve() {
        return SingletonHelper.INSTANCE;
    }

    private static class SingletonHelper {
        private static final JDKPackage INSTANCE = new JDKPackage();
    }
}
