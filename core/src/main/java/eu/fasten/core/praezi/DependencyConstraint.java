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
package eu.fasten.core.praezi;

import java.io.Serializable;
import java.util.Objects;


public class DependencyConstraint implements Serializable {

    public final String pkg;
    public final String version;
    public final String versionConstraint;

    public DependencyConstraint(String pkg, String version, String versionConstraint) {
        this.pkg = pkg;
        this.version = version;
        this.versionConstraint = versionConstraint;
    }

    @Override
    public String toString() {
        return pkg + "," + version + ":" + versionConstraint;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DependencyConstraint) {
            DependencyConstraint dc = (DependencyConstraint) o;
            return Objects.equals(dc.pkg, this.pkg) && Objects.equals(dc.version, this.version) && Objects.equals(dc.versionConstraint, this.versionConstraint);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pkg, this.version, this.versionConstraint);
    }
}
