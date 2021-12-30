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
package eu.fasten.analyzer.pomanalyzer;

import static eu.fasten.core.plugins.KafkaPlugin.ProcessingLane.NORMAL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.utils.DatabaseUtils;
import eu.fasten.analyzer.pomanalyzer.utils.EffectiveModelBuilder;
import eu.fasten.analyzer.pomanalyzer.utils.MavenRepositoryUtils;
import eu.fasten.analyzer.pomanalyzer.utils.PomExtractor;
import eu.fasten.analyzer.pomanalyzer.utils.Resolver;

public class POMAnalyzerPluginTest {

	private MavenRepositoryUtils repo;
	private EffectiveModelBuilder modelBuilder;
	private PomExtractor extractor;
	private DatabaseUtils db;
	private Resolver resolver;

	private POMAnalyzerPlugin.POMAnalyzer sut;

	@BeforeEach
	public void setup() {
		repo = mock(MavenRepositoryUtils.class);
		modelBuilder = mock(EffectiveModelBuilder.class);
		extractor = mock(PomExtractor.class);
		db = mock(DatabaseUtils.class);
		resolver = mock(Resolver.class);

		sut = new POMAnalyzerPlugin.POMAnalyzer(repo, modelBuilder, extractor, db, resolver);
		sut.setTopics(Collections.singletonList("fasten.mvn.pkg"));

		when(extractor.process(eq(null))).thenReturn(new PomAnalysisResult());
		when(extractor.process(any(Model.class))).thenReturn(new PomAnalysisResult());
	}

	@Test
	public void asd() {
		sut.consume("{\"groupId\":\"log4j\",\"artifactId\":\"log4j\",\"version\":\"1.2.17\"}", NORMAL);
	}

	// TODO extend test suite, right now this is only a stub for easy debugging
}