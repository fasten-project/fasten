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
import java.util.function.Consumer;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

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

    // TODO remove old handling once pipeline has been restarted
    private static final String OLD_VERSION_CONSTRAINTS = "versionConstraints";
    private static final String OLD_GROUP_ID = "groupId";
    private static final String OLD_ARTIFACT_ID = "artifactId";
    private static final String VERSION_CONSTRAINTS = "v";
    private static final String GROUP_ID = "g";
    private static final String ARTIFACT_ID = "a";

    private static final String JAR = "jar";

    private static final long serialVersionUID = 8302574258846915634L;

    public CoreMavenDataModule() {

        // PomAnalysisResult.class works out of the box

        addSerializer(Dependency.class, new JsonSerializer<Dependency>() {
            @Override
            public void serialize(Dependency value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeStartObject();

                gen.writeStringField(ARTIFACT_ID, value.artifactId);
                if (value.classifier != null && !value.classifier.isEmpty()) {
                    gen.writeStringField("classifier", value.classifier);
                }
                if (value.exclusions != null && !value.exclusions.isEmpty()) {
                    gen.writeObjectField("exclusions", value.exclusions);
                }
                gen.writeStringField(GROUP_ID, value.groupId);
                if (value.optional) {
                    gen.writeBooleanField("optional", value.optional);
                }
                if (value.scope != Scope.COMPILE) {
                    gen.writeStringField("scope", value.scope.toString().toLowerCase());
                }
                if (value.type != null && !"jar".equals(value.type)) {
                    gen.writeStringField("type", value.type);
                }
                gen.writeObjectField(VERSION_CONSTRAINTS, value.versionConstraints);

                gen.writeEndObject();
            }
        });
        addDeserializer(Dependency.class, new JsonDeserializer<Dependency>() {
            @Override
            public Dependency deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JacksonException {

                var oc = p.getCodec();
                var node = (JsonNode) oc.readTree(p);

                var a = getText(node, ARTIFACT_ID, OLD_ARTIFACT_ID);

                var c = node.has("classifier") ? node.get("classifier").asText() : "";

                var es = new LinkedHashSet<Exclusion>();
                if (node.has("exclusions")) {
                    node.get("exclusions").forEach(exclusion -> {
                        try {
                            es.add(ctxt.readTreeAsValue(exclusion, Exclusion.class));
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
                }

                var g = getText(node, GROUP_ID, OLD_GROUP_ID);
                var o = node.has("optional") ? node.get("optional").asBoolean() : false;
                var s = node.has("scope") ? Scope.valueOf(node.get("scope").asText().toUpperCase()) : Scope.COMPILE;
                var t = node.has("type") ? node.get("type").asText() : JAR;

                var vs = new LinkedHashSet<VersionConstraint>();
                Consumer<JsonNode> vcConsumer = vcjson -> {
                    try {
                        var vc = ctxt.readTreeAsValue(vcjson, VersionConstraint.class);
                        // TODO this check/fix will become irrelevant after the next pipeline reset
                        if (!vc.spec.isEmpty()) {
                            vs.add(vc);
                        }
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                };
                if (node.has(VERSION_CONSTRAINTS)) {
                    node.get(VERSION_CONSTRAINTS).forEach(vcConsumer);
                } else if (node.has(OLD_VERSION_CONSTRAINTS)) {
                    node.get(OLD_VERSION_CONSTRAINTS).forEach(vcConsumer);
                }

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

        addSerializer(DefaultArtifactVersion.class, new JsonSerializer<DefaultArtifactVersion>() {
            @Override
            public void serialize(DefaultArtifactVersion value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.toString());
            }
        });
        addDeserializer(DefaultArtifactVersion.class, new JsonDeserializer<DefaultArtifactVersion>() {
            @Override
            public DefaultArtifactVersion deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException, JacksonException {
                return new DefaultArtifactVersion(p.getValueAsString());
            }
        });
    }

    private static String getText(JsonNode node, String... keys) {
        for (var key : keys) {
            if (node.has(key)) {
                return node.get(key).textValue();
            }
        }
        return null;
    }
}