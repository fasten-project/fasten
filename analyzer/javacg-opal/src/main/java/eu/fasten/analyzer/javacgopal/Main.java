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

package eu.fasten.analyzer.javacgopal;

/**
 * Makes javacg-opal module runnable from command line.
 * Usage: java -jar javacg-opal-0.0.1-SNAPSHOT.jar groupId artifactId version timestamp
 */
public class Main {

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line parameters.
     *
     * @param args parameters should be in this sequence: "groupId" "artifactId" "version" "timestamp"
     * timestamp is the release timestamp of the artifact and should be in seconds from UNIX epoch.
     */
    public static void main(String[] args) {

        if(args.length != 4 || args[0].equals("--help")){
            System.out.println("Usage:\n" +
                "\tjava -jar javacg-opal-0.0.1-SNAPSHOT.jar <groupId> <artifactId> <version> <timestamp>");
        }else {

            MavenResolver.MavenCoordinate mavenCoordinate = new MavenResolver.MavenCoordinate(args[0], args[1], args[2]);

            var revisionCallGraph = PartialCallGraph.createRevisionCallGraph("mvn",
                new MavenResolver.MavenCoordinate(mavenCoordinate.getGroupID(), mavenCoordinate.getArtifactID(), mavenCoordinate.getVersion()),
                Long.parseLong(args[3]),
                CallGraphGenerator.generatePartialCallGraph(MavenResolver.downloadArtifact(mavenCoordinate.getCoordinate())));

            //TODO something with the calculated RevesionCallGraph.

        }

    }

}


