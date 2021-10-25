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

package eu.fasten.core.pypi.data;


import eu.fasten.core.data.Constants;

import java.util.Objects;

/**
 * A versionless Pypi Product
 */
public class PypiProduct {

    public long id;
    public String package_name;

    public PypiProduct(){}

    public PypiProduct(final String package_name) {
        this.id = 0;
        this.package_name = package_name;
    }

    public PypiProduct(long id, String package_name) {
        this.id = id;
        this.package_name = package_name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PypiProduct that = (PypiProduct) o;
        return package_name.equals(that.package_name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, package_name);
    }

    @Override
    public String toString() {
        return package_name;
    }
}
