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
package eu.fasten.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.c0ps.io.ObjectMapperBuilder;
import dev.c0ps.maven.json.CommonsMavenDataModule;

public class FastenObjectMapperBuilder extends ObjectMapperBuilder {

    @Override
    public ObjectMapper build() {
        var om = super.build();
        om.registerModule(new CommonsMavenDataModule());
        return om;
    }
}