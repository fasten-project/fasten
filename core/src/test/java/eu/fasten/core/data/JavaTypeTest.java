package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.HashBiMap;

class JavaTypeTest {

	@Test
	void testBiMap() {
		JavaType type0 = new JavaType("A", "A.java", HashBiMap.create(), new HashMap<>(), new LinkedList<>(), Collections.emptyList(), "public", false);
		JavaType type1 = new JavaType("B", "A.java", HashBiMap.create(), new HashMap<>(), new LinkedList<>(), Collections.emptyList(), "public", false);
		Map<String, JavaType> internals = new HashMap<>();
		internals.put("A", type0);
		internals.put("B", type1);
		assertEquals(2, internals.size());
	}

}
