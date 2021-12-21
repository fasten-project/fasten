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
package eu.fasten.core.plugins;

import java.util.List;
import java.util.Optional;

public abstract class AbstractKafkaPlugin implements KafkaPlugin {

	protected List<String> consumeTopics = null;
	protected Exception pluginError;

	@Override
	public String name() {
		return getClass().getSimpleName();
	}

	@Override
	public String description() {
		return "Description of " + name();
	}

	@Override
	public String version() {
		// TODO take from pom to avoid duplication?
		return "0.0.1-SNAPSHOT";
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public Exception getPluginError() {
		return this.pluginError;
	}

	@Override
	public void freeResource() {

	}

	@Override
	public Optional<List<String>> consumeTopic() {
		if(consumeTopics == null) {
			throw new RuntimeException("consumeTopics not set. Did you forget to add a startup parameter?");
		}
		return Optional.of(consumeTopics);
	}

	@Override
	public void setTopics(List<String> consumeTopics) {
		this.consumeTopics = consumeTopics;
	}

	@Override
	public abstract void consume(String record);

	@Override
	public Optional<String> produce() {
		throw new RuntimeException(
				"Not implemented. Override either 'produceMultiple' (preferred) or 'produce' and 'getOutputPath'.");
	}

	@Override
	public String getOutputPath() {
		throw new RuntimeException(
				"Not implemented. Override either 'produceMultiple' (preferred) or 'produce' and 'getOutputPath'.");
	}
}