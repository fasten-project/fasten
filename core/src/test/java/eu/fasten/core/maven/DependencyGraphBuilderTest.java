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

package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Revision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DependencyGraphBuilderTest {

    private DependencyGraphBuilder graphBuilder;

    @BeforeEach
    public void setup() {
        graphBuilder = new DependencyGraphBuilder();
    }

    @Test
    public void findMatchingRevisionsTest() {
        var revisions = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var constraints = List.of(
                new Dependency.VersionConstraint("(1.0,3.0]")
        );
        var expected = List.of(
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);

        constraints = List.of(
                new Dependency.VersionConstraint("[2.0,3.0]")
        );
        actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);
    }

    @Test
    public void findMatchingRevisionsMultipleConstraintsTest() {
        var revisions = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var constraints = List.of(
                new Dependency.VersionConstraint("[1.0,2.0)"),
                new Dependency.VersionConstraint("(2.0,3.0]")
        );
        var expected = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);
    }

    @Test
    public void findMatchingRevisionsSimpleTest() {
        var revisions = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var constraints = List.of(new Dependency.VersionConstraint("1.0"));
        var expected = List.of(new Revision("a", "a", "1.0", new Timestamp(1)));
        var actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);

        constraints = List.of(new Dependency.VersionConstraint("[1.0]"));
        actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);
    }

    @Test
    public void findMatchingRevisionsWithoutLowerOrUpperBoundTest() {
        var revisions = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        var constraints = List.of(new Dependency.VersionConstraint("(,2.0]"));
        var expected = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2))
        );
        var actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);

        constraints = List.of(new Dependency.VersionConstraint("[2.0,]"));
        expected = List.of(
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3))
        );
        actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);
    }

    @Test
    public void findMatchingRevisionsRangesWithRequirementsTest() {
        var revisions = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "2.0", new Timestamp(2)),
                new Revision("a", "a", "3.0", new Timestamp(3)),
                new Revision("a", "a", "4.0", new Timestamp(4))
        );
        var constraints = List.of(
                new Dependency.VersionConstraint("(,1.0]"),
                new Dependency.VersionConstraint("[3.0,)")
        );
        var expected = List.of(
                new Revision("a", "a", "1.0", new Timestamp(1)),
                new Revision("a", "a", "3.0", new Timestamp(3)),
                new Revision("a", "a", "4.0", new Timestamp(4))
        );
        var actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);

        constraints = List.of(
                new Dependency.VersionConstraint("(,2.0)"),
                new Dependency.VersionConstraint("(2.0,)")
        );
        actual = graphBuilder.findMatchingRevisions(revisions, constraints);
        assertEquals(expected, actual);
    }
}
