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
package eu.fasten.core.praezi;


import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Pattern;

public class SimplePackageVersion implements Serializable {


    public final String name;
    public final String version;
    //Regex for Node
    final String reNode = "\\W*(Node0x.*) \\[.*,label=\\\"\\{(.*)\\}\\\"\\];";
    final Pattern paNode = Pattern.compile(reNode);
    //Regex for Edge
    final String reEdge = "\\W*(Node0x.*) -> (Node0x.*);";
    final Pattern paEdge = Pattern.compile(reEdge);

    public SimplePackageVersion(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public CallGraph getCallGraph() throws IOException {
        var dotfile = "/Users/jhejderup/Desktop/fastengraphs/" + this.name + "/" + this.version + "/callgraph.unmangled.graph";
        var filePath = Path.of(dotfile);
        var nodes = new HashMap<String, String>();
        var edges = new HashMap<String, ArrayList<String>>();
        Files.lines(filePath).forEach(l -> {
            var node = paNode.matcher(l);
            if (node.find()) {
                //check if resolved or unresolved
                nodes.put(node.group(1), node.group(2));
            } else {
                var edge = paEdge.matcher(l);
                if (edge.find()) {
                    if (edges.containsKey(edge.group(1))) {
                        edges.get(edge.group(1)).add(edge.group(2)); //Add edge
                    } else {
                        var lst = new ArrayList<String>();
                        lst.add(edge.group(2));
                        edges.put(edge.group(1), lst);
                    }
                }
            }
        });
        return new CallGraph(nodes, edges);

    }

    @Override
    public String toString() {
        return name + "," + version;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SimplePackageVersion) {
            SimplePackageVersion dc = (SimplePackageVersion) o;
            return Objects.equals(dc.name, this.name) && Objects.equals(dc.version, this.version);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.version);
    }

}


