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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.fasten.core.data.collections.ImmutableEmptyLinkedHashSet;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Ids;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class CoreMavenDataModule extends SimpleModule {

    private static final Set<Exclusion> NO_EXCLS = Set.of();
    private static final Set<VersionConstraint> NO_VCS = Set.of();
    private static final String EMPTY_STR = "";
    private static final String JAR = "jar";

    // TODO remove old handling once pipeline has been restarted
    private static final String OLD_VERSION_CONSTRAINTS = "versionConstraints";
    private static final String OLD_GROUP_ID = "groupId";
    private static final String OLD_ARTIFACT_ID = "artifactId";
    private static final String VERSION_CONSTRAINTS = "v";
    private static final String GROUP_ID = "g";
    private static final String ARTIFACT_ID = "a";

    private static final long serialVersionUID = 8302574258846915634L;

    public CoreMavenDataModule() {

        addDeserializer(Pom.class, new PomDeserializer());

        addSerializer(Dependency.class, new JsonSerializer<Dependency>() {
            @Override
            public void serialize(Dependency value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeStartObject();

                gen.writeStringField(ARTIFACT_ID, value.artifactId);
                if (value.getClassifier() != null && !value.getClassifier().isEmpty()) {
                    gen.writeStringField("classifier", value.getClassifier());
                }
                if (value.getExclusions() != null && !value.getExclusions().isEmpty()) {
                    gen.writeObjectField("exclusions", value.getExclusions());
                }
                gen.writeStringField(GROUP_ID, value.groupId);
                if (value.isOptional()) {
                    gen.writeBooleanField("optional", value.isOptional());
                }
                if (value.getScope() != Scope.COMPILE) {
                    gen.writeStringField("scope", value.getScope().toString().toLowerCase());
                }
                if (value.getPackagingType() != null && !JAR.equals(value.getPackagingType())) {
                    gen.writeStringField("type", value.getPackagingType());
                }
                gen.writeObjectField(VERSION_CONSTRAINTS, value.getVersionConstraints());

                gen.writeEndObject();
            }
        });
        addDeserializer(Dependency.class, new DependencyDeserializer());

        addSerializer(VersionConstraint.class, new JsonSerializer<VersionConstraint>() {
            @Override
            public void serialize(VersionConstraint value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.getSpec());
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
                var version = Ids.version(p.getValueAsString());
                return new DefaultArtifactVersion(version);
            }
        });
    }

    private static class PomDeserializer extends JsonDeserializer<Pom> {

        private static final LinkedHashSet<Dependency> NO_DEPS = new ImmutableEmptyLinkedHashSet<>();
        private static final Set<Dependency> NO_DEP_MGMT = Set.of();

        @Override
        public Pom deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            var node = readNode(p);
            var pb = new PomBuilder();

            pb.groupId = getText(node, "groupId", null);
            pb.artifactId = getText(node, "artifactId", null);
            pb.packagingType = getText(node, "packagingType", null);
            pb.version = getText(node, "version", null);

            pb.parentCoordinate = getText(node, "parentCoordinate", null);

            pb.releaseDate = getLong(node, "releaseDate", -1L);
            pb.projectName = getText(node, "projectName", null);

            pb.dependencies = addChildren(node, p, "dependencies", () -> new LinkedHashSet<Dependency>(), NO_DEPS);
            pb.dependencyManagement = addChildren(node, p, "dependencyManagement", () -> new HashSet<Dependency>(),
                    NO_DEP_MGMT);

            pb.repoUrl = getText(node, "repoUrl", null);
            pb.commitTag = getText(node, "commitTag", null);
            pb.sourcesUrl = getText(node, "sourcesUrl", null);
            pb.artifactRepository = getText(node, "artifactRepository", null);

            return pb.pom();
        }
    }

    private static class DependencyDeserializer extends JsonDeserializer<Dependency> {

        @Override
        public Dependency deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            var node = readNode(p);

            var isShortFormat = node.has(ARTIFACT_ID);
            var a = getText(node, isShortFormat ? ARTIFACT_ID : OLD_ARTIFACT_ID, EMPTY_STR);
            var g = getText(node, isShortFormat ? GROUP_ID : OLD_GROUP_ID, EMPTY_STR);

            var c = getText(node, "classifier", EMPTY_STR);

            var es = readExclusions(ctxt, node);

            var o = getBool(node, "optional", false);
            var s = getScope(node, "scope", Scope.COMPILE);

            var t = getText(node, "type", JAR);

            var vs = readVersionConstraints(isShortFormat, ctxt, node);

            return Ids.dep(new Dependency(g, a, vs, es, s, o, t, c));
        }
    }

    private static Set<VersionConstraint> readVersionConstraints(boolean isShortFormat, DeserializationContext ctxt,
            JsonNode node) {
        var vcs = isShortFormat ? node.get(VERSION_CONSTRAINTS) : node.get(OLD_VERSION_CONSTRAINTS);
        if (vcs != null && !vcs.isEmpty()) {
            var res = new HashSet<VersionConstraint>(vcs.size() + 1, 1);
            for (var vc : vcs) {
                var val = vc.textValue();
                if (val == null || val.isEmpty()) {
                    // TODO handling unnecessary after next pipeline restart
                    continue;
                }
                var v = Ids.versionConstraint(new VersionConstraint(val));
                if (!v.getSpec().isEmpty()) {
                    res.add(v);
                }
            }
            // TODO remove as well (handling necessary to capture fix)
            if (res.isEmpty()) {
                return NO_VCS;
            }
            return res;
        }
        return NO_VCS;
    }

    private static Set<Exclusion> readExclusions(DeserializationContext ctxt, JsonNode node) {
        var exclusions = node.get("exclusions");
        if (exclusions != null && !exclusions.isEmpty()) {
            var es = new HashSet<Exclusion>(exclusions.size() + 1, 1);
            for (var exclusion : exclusions) {
                var parts = exclusion.textValue().split(":");
                es.add(new Exclusion(parts[0], parts[1]));
            }
            return es;
        }
        return NO_EXCLS;
    }

    private static String getText(JsonNode node, String key, String defaultVal) {
        var val = node.get(key);
        return val != null ? val.textValue() : defaultVal;
    }

    private static Scope getScope(JsonNode node, String key, Scope defaultVal) {
        var val = node.get(key);
        return val != null ? Scope.valueOf(val.textValue().toUpperCase()) : defaultVal;
    }

    private static boolean getBool(JsonNode node, String key, boolean defaultVal) {
        var val = node.get(key);
        return val != null ? val.booleanValue() : defaultVal;
    }

    private static long getLong(JsonNode node, String key, long defaultVal) {
        var val = node.get(key);
        return val != null ? val.longValue() : defaultVal;
    }

    private static ObjectNode readNode(JsonParser p) throws IOException {
        return (ObjectNode) p.getCodec().readTree(p);
    }

    private static <CollectionT extends Collection<Dependency>> CollectionT addChildren(JsonNode node, JsonParser p,
            String key, Supplier<CollectionT> prod, CollectionT defaultVal) throws JsonProcessingException {
        var vals = node.get(key);
        if (vals != null && !vals.isEmpty()) {
            var ts = prod.get();
            for (var val : vals) {
                var t = p.getCodec().treeToValue(val, Dependency.class);
                ts.add(Ids.dep(t));
            }
            return ts;
        }
        return defaultVal;
    }
}