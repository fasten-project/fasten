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

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/**
 * A version of a package in a package dependency network. This effectively
 * represents a node in such network. To support retroactive dependency
 * resolution, the {@link PackageVersion#releaseDate} field stores the
 * date this version was created in the package repository. The
 * {@link PackageVersion#version} corresponds to a repository-specific
 * version specification. Most package repositories use some form of
 * semantic versioning.
 *
 * The PackageVersion is also the entry point to the fine-grained,
 * call-based dependency network, through the {@link PackageVersion#functions}
 * field.
 *
 * <h2>Dependency resolution</h2>
 *
 * The data model does not include the concept of dependency resolution, which
 * it delegates to repository-specific algorithms. However, it does need to
 * support retroactive dependency resolution, at any point in the repository's
 * lifetime. The general resolution algorithm for each dependency in
 * {@link PackageVersion#dependencies} goes as follows:
 *
 * <ol>
 *     <li>Search the package version index for all PackageVersions that
 *     for package {@link Dependency#pkg} that satisfy the repository specific
 *     {@link Dependency#versionConstraint}</li>
 *
 *     <li>From the list above, keep the first PackageVersion whose
 *     {@link #releaseDate} is less than {@link this#releaseDate}</li>
 *
 *     <li>Add all {@link Function}s in the identified PackageVersion to the
 *     set of functions to return.</li>
 * </ol>
 *
 * This will give us a set of {@link Function}s, of which some will be
 * {@link UnresolvedFunction}s. To resolve them, we need to apply a
 * language-specific resolution mechanism to match each
 * {@link UnresolvedFunction} to a {@link ResolvedFunction} in our result set.
 */
public class PackageVersion implements Serializable {

    public final Package pkg;
    public final String version;
    public final Date releaseDate;
    public final Set<Dependency> dependencies;
    public final Set<Function> functions;


    public PackageVersion(Package pkg, String version, Date releaseDate,
                          Set<Dependency> dependencies, Set<Function> functions) {
        this.pkg = pkg;
        this.version = version;
        this.releaseDate = releaseDate;
        this.dependencies = dependencies;
        this.functions = functions;
    }
}
