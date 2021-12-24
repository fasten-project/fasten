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
package eu.fasten.analyzer.pomanalyzer.utils;

import java.io.File;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;

import fr.inria.spirals.repairnator.process.maven.RepositoryModelResolver;

public class EffectiveModelBuilder {

	public static final File LOCAL_M2 = MavenRepositoryUtils.getPathOfLocalRepository();

	public Model buildEffectiveModel(File pom) {

		try {
			var factory = new DefaultModelBuilderFactory();
			var builder = factory.newInstance();

			var req = new DefaultModelBuildingRequest();
			req.setProcessPlugins(false);
			req.setModelResolver(new RepositoryModelResolver(LOCAL_M2));
			req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
			req.setPomFile(pom);

			var buildingResult = builder.build(req);
			var model = buildingResult.getEffectiveModel();
			return model;
		} catch (ModelBuildingException e) {
			throw new RuntimeException(e);
		}
	}
}