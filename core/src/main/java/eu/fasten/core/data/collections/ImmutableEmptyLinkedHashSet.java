/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.data.collections;

import java.util.Collection;
import java.util.LinkedHashSet;

// required for "Pom"
public class ImmutableEmptyLinkedHashSet<T> extends LinkedHashSet<T> {

    private static final long serialVersionUID = -7233644259488131119L;

    // will always be empty, so preventing "add" variants is all that is necessary

    public boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }
}