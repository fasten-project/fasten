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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ImmutableEmptyLinkedHashSetTest {

    private ImmutableEmptyLinkedHashSet<String> sut;

    @BeforeEach
    public void setup() {
        sut = new ImmutableEmptyLinkedHashSet<>();
    }

    @Test
    public void cannotAdd() {
        assertThrows(UnsupportedOperationException.class, () -> {
            sut.add("...");
        });
    }

    @Test
    public void cannotAddAll() {
        assertThrows(UnsupportedOperationException.class, () -> {
            sut.addAll(Set.of("..."));
        });
    }
}