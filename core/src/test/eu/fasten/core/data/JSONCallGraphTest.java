package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.JSONCallGraph.Constraint;
import eu.fasten.core.data.JSONCallGraph.Dependency;

class JSONCallGraphTest {

	@Test
	void testConstraint() {
		JSONCallGraph.Constraint c;
		
		c = new JSONCallGraph.Constraint("[3.1..7.4]");
		assertEquals("3.1", c.lowerBound);
		assertEquals("7.4", c.upperBound);
		
		c = new JSONCallGraph.Constraint("[3.1..]");
		assertEquals("3.1", c.lowerBound);
		assertNull(c.upperBound);
		
		c = new JSONCallGraph.Constraint("[   ..  3.1]");
		assertNull(c.lowerBound);
		assertEquals("3.1", c.upperBound);

		
		c = new JSONCallGraph.Constraint("[3.1]");
		assertEquals("3.1", c.lowerBound);
		assertEquals("3.1", c.upperBound);

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new JSONCallGraph.Constraint("joooo");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new JSONCallGraph.Constraint("[a..b..c]");
		});
		
		JSONArray cs = new JSONArray("[\"[3.1..  7.1   ]\",\"[   9]\",\"[10.3  ..]\"]");
		Constraint[] constraints = JSONCallGraph.Constraint.constraints(cs);
		assertEquals(3, constraints.length);
		assertEquals("3.1", constraints[0].lowerBound);
		assertEquals("7.1", constraints[0].upperBound);
		assertEquals("9", constraints[1].lowerBound);
		assertEquals("9", constraints[1].upperBound);
		assertEquals("10.3", constraints[2].lowerBound);
		assertNull(constraints[2].upperBound);
	}
	
	@Test
	void testDependency() {
		JSONCallGraph.Dependency d;
		d = new JSONCallGraph.Dependency("maven", "foo.bar", new Constraint[] {new Constraint("[3.1..7.1]")});
		assertEquals("maven", d.forge);
		assertEquals("foo.bar", d.product);
		assertEquals(1, d.constraints.length);
		assertEquals("3.1", d.constraints[0].lowerBound);
		assertEquals("7.1", d.constraints[0].upperBound);
		
		JSONObject json = new JSONObject("{\"forge\": \"maven\", \"product\": \"foo.bar\", \"constraints\": [\"[3.1..  7.1   ]\",\"[   9]\",\"[10.3  ..]\"] }");
		d = new JSONCallGraph.Dependency(json, false);
		assertEquals("maven", d.forge);
		assertEquals("foo.bar", d.product);
		Constraint[] constraints = d.constraints;
		assertEquals(3, constraints.length);
		assertEquals("3.1", constraints[0].lowerBound);
		assertEquals("7.1", constraints[0].upperBound);
		assertEquals("9", constraints[1].lowerBound);
		assertEquals("9", constraints[1].upperBound);
		assertEquals("10.3", constraints[2].lowerBound);
		assertNull(constraints[2].upperBound);
	}
	
	@Test
	void testDepset() {
		JSONArray json = new JSONArray("[" +
				"{\"forge\": \"maven\", \"product\": \"foo.bar\", \"constraints\": [\"[3.1..  7.1   ]\",\"[   9]\",\"[10.3  ..]\"] }, " +
				"{\"forge\": \"other\", \"product\": \"bar.nee\", \"constraints\": [\"[..9]\",\"[10.3  ..]\"] }" +
				"]");
		Dependency[] depset = JSONCallGraph.Dependency.depset(json, false);
		Dependency d = depset[0];
		assertEquals("maven", d.forge);
		assertEquals("foo.bar", d.product);
		Constraint[] constraints = d.constraints;
		assertEquals(3, constraints.length);
		assertEquals("3.1", constraints[0].lowerBound);
		assertEquals("7.1", constraints[0].upperBound);
		assertEquals("9", constraints[1].lowerBound);
		assertEquals("9", constraints[1].upperBound);
		assertEquals("10.3", constraints[2].lowerBound);
		assertNull(constraints[2].upperBound);
		d = depset[1];
		assertEquals("other", d.forge);
		assertEquals("bar.nee", d.product);
		constraints = d.constraints;
		assertEquals(2, constraints.length);
		assertNull(constraints[0].lowerBound);
		assertEquals("9", constraints[0].upperBound);
		assertEquals("10.3", constraints[1].lowerBound);
		assertNull(constraints[1].upperBound);
	}

	@Test
	void testCallGraph() throws JSONException, URISyntaxException {
		String callGraph = "{\n" + 
				"    \"forge\": \"mvn\",\n" + 
				"    \"product\": \"foo\",\n" + 
				"    \"version\": \"2.0\",\n" + 
				"    \"depset\":\n" + 
				"      [\n" + 
				"        { \"forge\": \"mvn\", \"product\": \"a\", \"constraints\": [\"[1.0..2.0]\", \"[4.2..]\"]},\n" + 
				"        { \"forge\": \"other\", \"product\": \"b\", \"constraints\": [\"[4.3.2]\"]}\n" + 
				"      ],\n" + 
				"    \"graph\": \n" + 
				"      [\n" + 
				"        [\"/my.package/A.f(A)B\",\n" + 
				"         \"/my.other.package/C.g(%2Fmy.package%2FA)%2Fmy.package%2FB\"],\n" + 
				"        [\"/my.package/A.g(A,%2F%2Fjdk%2Fjava.lang%2Fint)%2F%2Fjdk%2Fjava.lang%2Fint\",\n" + 
				"         \"//b/their.package/TheirClass.method(TheirOtherClass)TheirOtherClass\"],\n" + 
				"      ]\n" + 
				"}";
		JSONObject json = new JSONObject(callGraph);
		JSONCallGraph cg = new JSONCallGraph(json, false, null);
		assertEquals("mvn", cg.forge);
		assertEquals("foo", cg.product);
		assertEquals("2.0", cg.version);
		Dependency[] depset = cg.depset;
		assertEquals(2, depset.length);
		assertEquals("mvn", depset[0].forge);
		assertEquals("a", depset[0].product);
		Constraint[] constraints = depset[0].constraints;
		assertEquals(2, constraints.length);
		assertEquals("1.0", constraints[0].lowerBound);
		assertEquals("2.0", constraints[0].upperBound);
		assertEquals("4.2", constraints[1].lowerBound);
		assertNull(constraints[1].upperBound);
		assertEquals("mvn", depset[0].forge);
		assertEquals("a", depset[0].product);
		constraints = depset[1].constraints;
		assertEquals(1, constraints.length);
		assertEquals("4.3.2", constraints[0].lowerBound);
		assertEquals("4.3.2", constraints[0].upperBound);
	}

}
