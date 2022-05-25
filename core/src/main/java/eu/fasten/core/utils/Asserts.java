/*
 * Copyright 2021 Delft University of Technology
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
package eu.fasten.core.utils;

public class Asserts {

    private Asserts() {
        // do not initialize this class
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw $("Should not be null.");
        }
    }

    public static <T> void assertContains(T[] ts, T e) {
        for (T a : ts) {
            if (a == null) {
                if (e == null) {
                    return;
                }
            } else {
                if (a.equals(e)) {
                    return;
                }
            }
        }
        throw $("Expected element '%s' not contained in array.", e);
    }

    public static void assertNotNullOrEmpty(String s) {
        if (s == null || s.isEmpty()) {
            throw $("String is null or empty.");
        }
    }

    public static void assertTrue(boolean condition, String failMsg) {
        if (!condition) {
            throw $(failMsg);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw $("Expected condition not met.");
        }
    }

    // TODO add more assertions as we go

    private static IllegalStateException $(String msg, Object... args) {
        String s = String.format(msg, args);
        return new IllegalStateException(s);
    }
}