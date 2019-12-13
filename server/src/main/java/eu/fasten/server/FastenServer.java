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

package eu.fasten.server;

//import eu.fasten.analyzer.plugins.DummyAnalyzer;
import eu.fasten.core.plugins.FastenPlugin;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionWrapper;
import org.pf4j.JarPluginManager;
import org.pf4j.RuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static eu.fasten.server.utils.StreamUtils.asStream;

@CommandLine.Command(name = "FastenServer", mixinStandardHelpOptions = true)
public class FastenServer implements Runnable {
    public final static String PLUGIN_TEMPLATE = "\\.jar$";

    @Option(names = {"-p", "--plugin_dir"},
            paramLabel = "DIR",
            description = "Directory to load plugins from",
            defaultValue = ".")
    private Path pluginPath;

    @Option(names= {"-k", "--kafka_servers"})
    private List<String> kafkaServers;

    private static Logger logger = LoggerFactory.getLogger(FastenServer.class);

    public Stream<Path> findPlugins(Path dir) throws IOException {
        return asStream(
                Files.newDirectoryStream(dir,
                        file -> file.toString().matches(PLUGIN_TEMPLATE)).iterator()
        );
    }

    public Optional<FastenPlugin> loadPlugin(Path plugin) {
        return null;
    }

    public void run() {
        logger.debug("Loading plugins from: {}", pluginPath.toAbsolutePath());
        try {
            findPlugins(pluginPath.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        int exitCode = new CommandLine(new FastenServer()).execute(args);
//        System.exit(exitCode);


        JarPluginManager jarPluginManager = new JarPluginManager(Paths.get("/Users/amir/projects/fasten/server/src/main/java/eu/fasten/server/plugins"));
        jarPluginManager.loadPlugins();
        //System.setProperty("pf4j.mode", RuntimeMode.DEVELOPMENT.toString());

        System.out.println("Mode: " + jarPluginManager.getRuntimeMode().toString());

        List<FastenPlugin> plugins = jarPluginManager.getExtensions(FastenPlugin.class);

        for(FastenPlugin p : plugins){
            System.out.println("Name: " + p.name());
        }

    }
}
