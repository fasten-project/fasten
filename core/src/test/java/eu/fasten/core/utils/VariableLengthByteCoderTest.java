/* Licensed to the Apache Software Foundation (ASF) under one
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

package eu.fasten.core.utils;

import static eu.fasten.core.utils.VariableLengthByteCoder.readByteArray;
import static eu.fasten.core.utils.VariableLengthByteCoder.readLong;
import static eu.fasten.core.utils.VariableLengthByteCoder.readString;
import static eu.fasten.core.utils.VariableLengthByteCoder.writeByteArray;
import static eu.fasten.core.utils.VariableLengthByteCoder.writeLong;
import static eu.fasten.core.utils.VariableLengthByteCoder.writeString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class VariableLengthByteCoderTest {

	private static final long UPPER_BOUND_1 = 128;
	private static final long UPPER_BOUND_2 = 128 * 128 + UPPER_BOUND_1;
	private static final long UPPER_BOUND_3 = 128 * 128 * 128 + UPPER_BOUND_2;
	private static final long UPPER_BOUND_4 = 128 * 128 * 128 * 128 + UPPER_BOUND_3;
	private static final long UPPER_BOUND_5 = 128L * 128 * 128 * 128 * 128 + UPPER_BOUND_4;
	private static final long UPPER_BOUND_6 = 128L * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_5;
	private static final long UPPER_BOUND_7 = 128L * 128 * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_6;
	private static final long UPPER_BOUND_8 = 128L * 128 * 128 * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_7;

	public static long[] TEST_SEQUENCE = { 0, UPPER_BOUND_1 - 1, UPPER_BOUND_1, UPPER_BOUND_2 - 1, UPPER_BOUND_2,
			UPPER_BOUND_3 - 1, UPPER_BOUND_3, UPPER_BOUND_4 - 1, UPPER_BOUND_4, UPPER_BOUND_5 - 1, UPPER_BOUND_5,
			UPPER_BOUND_6 - 1, UPPER_BOUND_6, UPPER_BOUND_7 - 1, UPPER_BOUND_7, UPPER_BOUND_8 - 1, UPPER_BOUND_8 };

	@Test
	public void testBounds() throws IOException {
		final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
		for (final long x : TEST_SEQUENCE) writeLong(x, fbaos);
		fbaos.flush();
		final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
		for (final long x : TEST_SEQUENCE) assertEquals(x, readLong(fbais));
	}

	@Test
	public void testRandomLongs() throws IOException {
		final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
		final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
		for (int i = 0; i < 100000; i++) writeLong(r.nextLong() & (-1L >>> 1), fbaos);
		fbaos.flush();
		r.setSeed(0);
		final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
		for (int i = 0; i < 100000; i++) assertEquals(r.nextLong() & (-1L >>> 1), readLong(fbais));
	}

	@Test
	public void testRandomInts() throws IOException {
		final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
		final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
		for (int i = 0; i < 100000; i++) writeLong(r.nextInt() & (-1L >>> 32), fbaos);
		fbaos.flush();
		r.setSeed(0);
		final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
		for (int i = 0; i < 100000; i++) assertEquals(r.nextInt() & (-1L >>> 32), readLong(fbais));
	}

	@Test
	public void testByteArrays() throws IOException {
		final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
		byte[] a = { 1, 7, 3, 5 };
		writeByteArray(a, fbaos);
		fbaos.flush();
		final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
		assertArrayEquals(a, readByteArray(fbais));
	}

	@Test
	public void testStrings() throws IOException {
		final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
		String s = "abcd\u0410";
		writeString(s, fbaos);
		fbaos.flush();
		final FastByteArrayInputStream fbais = new FastByteArrayInputStream(fbaos.array, 0, fbaos.length);
		assertEquals(s, readString(fbais));
	}
}
