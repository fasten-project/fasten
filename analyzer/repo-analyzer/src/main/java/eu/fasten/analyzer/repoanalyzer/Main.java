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

package eu.fasten.analyzer.repoanalyzer;

import org.json.JSONObject;
import picocli.CommandLine;

@CommandLine.Command(name = "RepoAnalyzer", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    @CommandLine.Option(names = {"-p", "--path"},
            paramLabel = "PATH",
            description = "Path to the repository",
            defaultValue = "")
    String repoPath;

    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

    public void run() {
        var analyzer = new RepoAnalyzerPlugin.RepoAnalyzerExtension();
        var json = new JSONObject();
        json.put("repoPath", repoPath);
        analyzer.consume(json.toString());
        var result = analyzer.produce();
        result.ifPresent(s -> System.out.println(new JSONObject(s).toString(4)));
    }
}
