package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

class JavaTypeTest {

	@Test
	void testBiMap() {
		JavaType type0 = new JavaType("A.java", HashBiMap.create(), new HashMap<>(), new LinkedList<>(), Collections.emptyList(), "public", false);
		JavaType type1 = new JavaType("A.java", HashBiMap.create(), new HashMap<>(), new LinkedList<>(), Collections.emptyList(), "public", false);
		BiMap<String, JavaType> internals = HashBiMap.create();
		internals.forcePut("fasten://a/b", type0);
		internals.forcePut("fasten://c/d", type1);
		assertEquals(2, internals.size());
	}

}
