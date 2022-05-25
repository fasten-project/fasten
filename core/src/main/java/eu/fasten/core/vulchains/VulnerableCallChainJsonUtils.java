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

package eu.fasten.core.vulchains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import java.io.IOException;
import java.lang.reflect.Type;

public class VulnerableCallChainJsonUtils {


    private static Gson getGson() {
        return new GsonBuilder()//
         .registerTypeAdapter(FastenURI.class,
            new TypeAdapter<FastenURI>() {
            @Override
            public void write(JsonWriter jsonWriter, FastenURI fastenURI) throws IOException {
                jsonWriter.value(fastenURI.toString());
            }

            @Override
            public FastenURI read(JsonReader jsonReader) throws IOException {
                final var uri = jsonReader.nextString();
                FastenURI fastenURI;
                if (uri.startsWith("fasten://mvn")) {
                    fastenURI = FastenJavaURI.create(uri);
                }else {
                    fastenURI = FastenURI.create(uri);
                }
                return fastenURI;
            }
        }) //
         .create();
    }

    public static <T> String toJson(T obj) {
        return getGson().toJson(obj);
    }

    public static <T> T fromJson(String json, Type classOfT) {
        return getGson().fromJson(json, classOfT);
    }

}
