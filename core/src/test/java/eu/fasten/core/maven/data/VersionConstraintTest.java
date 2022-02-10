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
package eu.fasten.core.maven.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class VersionConstraintTest {

	// https://maven.apache.org/pom.html#dependency-version-requirement-specification

	@Test
	public void parsing1() {
		validate("1.0", //
				vc("1.0", false, "1.0", false));
	}

	@Test
	public void parsing2() {
		validate("[1.0]", //
				vc("1.0", true, "1.0", true));
	}

	@Test
	public void parsing3() {
		validate("(,1.0]", //
				vc("", false, "1.0", true));
	}

	@Test
	public void parsing4() {
		validate("[1.2,1.3]", //
				vc("1.2", true, "1.3", true));
	}

	@Test
	public void parsing5() {
		validate("[1.0,2.0)", //
				vc("1.0", true, "2.0", false));
	}

	@Test
	public void parsing6() {
		validate("[1.5,)", //
				vc("1.5", true, "", false));
	}

	@Test
	public void parsing7() {
		validate("(,1.0],[1.2,)", //
				vc("", false, "1.0", true), //
				vc("1.2", true, "", false));
	}

	@Test
	public void parsing8() {
		validate("(,1.1),(1.1,)", //
				vc("", false, "1.1", false), //
				vc("1.1", false, "", false));
	}

	@Test
	public void equality() {
		for (var spec : new String[] { "1.0", "[1.0]", "(,1.0]", "[1.2,1.3]", "[1.0,2.0)", "[1.5,)" }) {
			var a = new VersionConstraint(spec);
			var b = new VersionConstraint(spec);
			assertEquals(a, b);
			assertEquals(a.hashCode(), b.hashCode());
		}
	}

	private static void validate(String spec, Consumer<VersionConstraint>... validators) {
		var vcs = List.copyOf(VersionConstraint.resolveMultipleVersionConstraints(spec));
		assertEquals(validators.length, vcs.size());
		for (var i = 0; i < vcs.size(); i++) {
			validators[i].accept(vcs.get(i));
		}
	}

	private static Consumer<VersionConstraint> vc(String lowerBound, boolean hasHardLowerBound, String upperBound,
			boolean hasHardUpperBound) {
		return vcs -> {
			assertEquals(hasHardLowerBound, vcs.isLowerHardRequirement);
			assertEquals(lowerBound, vcs.lowerBound);
			assertEquals(hasHardUpperBound, vcs.isUpperHardRequirement);
			assertEquals(upperBound, vcs.upperBound);
		};
	}
}