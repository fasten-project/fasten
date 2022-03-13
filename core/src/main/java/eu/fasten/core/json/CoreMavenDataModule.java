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
package eu.fasten.core.json;

import java.io.IOException;
import java.util.LinkedHashSet;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class CoreMavenDataModule extends SimpleModule {

    private static final long serialVersionUID = 8302574258846915634L;

    public CoreMavenDataModule() {

        // PomAnalysisResult.class works out of the box

        addSerializer(Dependency.class, new JsonSerializer<Dependency>() {
            @Override
            public void serialize(Dependency value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeStartObject();

                gen.writeStringField("artifactId", value.artifactId);
                gen.writeStringField("classifier", value.classifier);
                gen.writeObjectField("exclusions", value.exclusions);
                gen.writeStringField("groupId", value.groupId);
                gen.writeBooleanField("optional", value.optional);
                gen.writeStringField("scope", value.scope.toString().toLowerCase());
                gen.writeStringField("type", value.type);
                gen.writeObjectField("versionConstraints", value.versionConstraints);

                gen.writeEndObject();
            }
        });
        addDeserializer(Dependency.class, new JsonDeserializer<Dependency>() {
            @Override
            public Dependency deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JacksonException {

                var oc = p.getCodec();
                var node = (JsonNode) oc.readTree(p);

                var a = node.get("artifactId").textValue();
                var c = node.get("classifier").asText();

                var es = new LinkedHashSet<Exclusion>();
                node.get("exclusions").forEach(exclusion -> {
                    try {
                        es.add(ctxt.readTreeAsValue(exclusion, Exclusion.class));
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });

                var g = node.get("groupId").asText();
                var o = node.get("optional").asBoolean();
                var s = Scope.valueOf(node.get("scope").asText().toUpperCase());
                var t = node.get("type").asText();

                var vs = new LinkedHashSet<VersionConstraint>();
                node.get("versionConstraints").forEach(vc -> {
                    try {
                        vs.add(ctxt.readTreeAsValue(vc, VersionConstraint.class));
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });

                return new Dependency(g, a, vs, es, s, o, t, c);

            }
        });

        addSerializer(VersionConstraint.class, new JsonSerializer<VersionConstraint>() {
            @Override
            public void serialize(VersionConstraint value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.spec);
            }
        });
        addDeserializer(VersionConstraint.class, new JsonDeserializer<VersionConstraint>() {
            @Override
            public VersionConstraint deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JacksonException {
                return new VersionConstraint(p.getValueAsString());
            }
        });

        addSerializer(Exclusion.class, new JsonSerializer<Exclusion>() {
            @Override
            public void serialize(Exclusion value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(String.format("%s:%s", value.groupId, value.artifactId));
            }
        });
        addDeserializer(Exclusion.class, new JsonDeserializer<Exclusion>() {
            @Override
            public Exclusion deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JacksonException {
                String[] parts = p.getValueAsString().split(":");
                return new Exclusion(parts[0], parts[1]);
            }
        });
    }
}