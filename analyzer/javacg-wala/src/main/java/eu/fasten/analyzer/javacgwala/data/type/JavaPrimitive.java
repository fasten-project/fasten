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

import com.ibm.wala.types.TypeReference;

public enum JavaPrimitive implements Namespace {
    BOOLEAN, BYTE, CHAR, DOUBLE,
    FLOAT, INT, LONG, SHORT,
    VOID, UNKNOWN;

    /**
     * Return java primitive type based on {@link TypeReference}.
     *
     * @param tyref - type reference
     * @return - java primitive
     */
    public static JavaPrimitive of(TypeReference tyref) {
        String s = tyref.getName().toString();

        assert tyref.isPrimitiveType();
        assert s.length() > 0;

        switch (s.charAt(0)) {
            case TypeReference.BooleanTypeCode:
                return BOOLEAN;
            case TypeReference.ByteTypeCode:
                return BYTE;
            case TypeReference.CharTypeCode:
                return CHAR;
            case TypeReference.DoubleTypeCode:
                return DOUBLE;
            case TypeReference.FloatTypeCode:
                return FLOAT;
            case TypeReference.IntTypeCode:
                return INT;
            case TypeReference.LongTypeCode:
                return LONG;
            case TypeReference.ShortTypeCode:
                return SHORT;
            case TypeReference.VoidTypeCode:
                return VOID;
            default:
                return UNKNOWN;
        }
    }

    @Override
    public String[] getSegments() {
        return new String[]{"java", "primitive", this.name().toLowerCase()};
    }

    @Override
    public String getNamespaceDelim() {
        return ".";
    }
}


