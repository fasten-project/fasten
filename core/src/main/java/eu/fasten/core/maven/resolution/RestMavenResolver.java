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
package eu.fasten.core.maven.resolution;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Collection;
import java.util.Set;

import org.glassfish.jersey.client.ClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.fasten.core.json.ObjectMapperBuilder;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.Scope;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.ext.ContextResolver;

public class RestMavenResolver implements IMavenResolver {

    private static final GenericType<Set<Revision>> SET_OF_REV = new GenericType<Set<Revision>>() {};
    private static final ObjectMapper OM = new ObjectMapperBuilder().build();

    private WebTarget baseTarget;

    public RestMavenResolver(String baseUrl) {
        var ctxResolver = new ContextResolver<ObjectMapper>() {
            @Override
            public ObjectMapper getContext(Class<?> type) {
                return OM;
            }
        };
        baseTarget = ClientBuilder //
                .newClient(new ClientConfig(ctxResolver)) //
                .target(baseUrl).path("depgraph");
    }

    @Override
    public Set<Revision> resolveDependencies(Collection<String> gavs, ResolverConfig config) {
        return getBase("dependencies", config) //
                .request(APPLICATION_JSON) //
                .post(Entity.entity(gavs, APPLICATION_JSON), SET_OF_REV);
    }

    @Override
    public Set<Revision> resolveDependents(String gid, String aid, String version, ResolverConfig config) {
        return getBase("dependents", config) //
                .path(gid).path(aid).path(version) //
                .request(APPLICATION_JSON) //
                .get(SET_OF_REV);
    }

    private WebTarget getBase(String subpath, ResolverConfig config) {
        var base = baseTarget //
                .path(subpath) //
                .queryParam("timestamp", config.timestamp);
        if (config.depth != ResolverDepth.TRANSITIVE) {
            base = base.queryParam("depth", config.depth);
        }
        if (config.scope != Scope.RUNTIME) {
            base = base.queryParam("scope", config.scope);
        }
        if (config.includeOptional) {
            base = base.queryParam("includeOptional", true);
        }
        return base;
    }
}