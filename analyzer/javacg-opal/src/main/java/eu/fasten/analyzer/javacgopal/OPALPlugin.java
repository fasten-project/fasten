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

import eu.fasten.analyzer.baseanalyzer.AnalyzerPlugin;
import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import java.io.FileNotFoundException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.PluginWrapper;

public class OPALPlugin extends AnalyzerPlugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL extends AnalyzerPlugin.ANALYZER {

        private static String produceTopic = "opal_callgraphs";

        @Override
        public ExtendedRevisionCallGraph generateCallGraph(final MavenCoordinate mavenCoordinate,
                                                           final JSONObject kafkaConsumedJson) {
            try {
                return PartialCallGraph.createExtendedRevisionCallGraph(mavenCoordinate,
                    Long.parseLong(kafkaConsumedJson.get("date").toString()));
            } catch (FileNotFoundException e) {
                setPluginError(e);
            }
            return null;
        }

        @Override
        public String producerTopic() {
            return produceTopic;
        }

        @Override
        public void setProducerTopic(String topicName) {
            produceTopic = topicName;
        }
    }
}
