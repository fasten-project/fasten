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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility methods for writing and reading data in a byte stream using variable-length byte
 * encoding.
 *
 * <p>
 * This class implements a variable-length byte encoding for longs that is not redundant on small
 * values, as it happens in a trivial &ldquo;continuation bit&rdquo; implementation. More precisely,
 * setting <var>u</var><sub>0</sub>&nbsp;=&nbsp;0 and
 * <var>u</var><sub><var>i</var>+1</sub>&nbsp;=&nbsp;128<sup><var><var>i</var>+1</var></sup>&nbsp;+&nbsp;<var>u</var><sub><var>i</var></sub>,
 * a long <var>x</var> smaller than <var>u</var><sub><var>i</var>+1</sub> but larger than or equal
 * to <var>u</var><sub><var>i</var></sub> is encoded by <var>i</var> bits set to one, one bit set to
 * zero, and then <var>x</var>&nbsp;&minus;&nbsp;<var>u</var><sub><var>i</var></sub> written using
 * 7(<var>i</var>&nbsp;+&nbsp;1) bits. For example, values smaller than 128 are stored as such in a
 * byte, whereas values between 128 and 16512 are stored in two bytes, the highest two bits of the
 * first byte being {@code 10}, and the remaining 14 bits storing the offset from 128.
 *
 * <p>
 * This encoding provides better compression for small values than a trivial implementation (e.g.,
 * values smaller than 16512 are encoded with two bytes, rather than values smaller than 16384, as
 * it happens usually). Moreover, the length of the code is stored in the first byte, which pleases
 * the branch predictor. Longs greater than or equal to
 * <var>u</var><sub>8</sub>&nbsp;=&nbsp;{@code 0x102040810204080} are encoded explicitly after
 * {@code 0xFF}.
 *
 * <p>
 * Note that moving continuation bits to the front is a folklore technique, and the same is true of
 * the reduction of redundancy (see, e.g., Git's encoder).
 * 
 * <p>
 * Using the encoding above, the class provides also utility methods to write
 * {@linkplain #writeByteArray(byte[], OutputStream) byte arrays} and
 * {@linkplain #writeString(String, OutputStream) strings}.
 *
 * @author Sebastiano Vigna
 */

public class VariableLengthByteCoder {
	// UPPER_BOUND_0 = 0 (implicit)
	private static final long UPPER_BOUND_1 = 128L;
	private static final long UPPER_BOUND_2 = 128L * 128 + UPPER_BOUND_1;
	private static final long UPPER_BOUND_3 = 128L * 128 * 128 + UPPER_BOUND_2;
	private static final long UPPER_BOUND_4 = 128L * 128 * 128 * 128 + UPPER_BOUND_3;
	private static final long UPPER_BOUND_5 = 128L * 128 * 128 * 128 * 128 + UPPER_BOUND_4;
	private static final long UPPER_BOUND_6 = 128L * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_5;
	private static final long UPPER_BOUND_7 = 128L * 128 * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_6;
	private static final long UPPER_BOUND_8 = 128L * 128 * 128 * 128 * 128 * 128 * 128 * 128 + UPPER_BOUND_7;

	private VariableLengthByteCoder() {
	}

	/**
	 * Writes a long using a variable-length prefix-free code.
	 *
	 * @param x a nonnegative long.
	 * @param os an output stream.
	 */
	public static void writeLong(long x, final OutputStream os) throws IOException {
		assert x >= 0;
		if (x < UPPER_BOUND_1) os.write((int)x);
		else if (x < UPPER_BOUND_2) {
			x -= UPPER_BOUND_1;
			assert (x >>> 8) < (1 << 6);
			os.write((int)(0x80 | x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_3) {
			x -= UPPER_BOUND_2;
			assert (x >>> 16) < (1 << 5);
			os.write((int)(0xC0 | x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_4) {
			x -= UPPER_BOUND_3;
			assert (x >>> 24) < (1 << 4);
			os.write((int)(0xE0 | x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_5) {
			x -= UPPER_BOUND_4;
			assert (x >>> 32) < (1 << 3);
			os.write((int)(0xF0 | x >>> 32));
			os.write((int)(x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_6) {
			x -= UPPER_BOUND_5;
			assert (x >>> 40) < (1 << 2);
			os.write((int)(0xF8 | x >>> 40));
			os.write((int)(x >>> 32));
			os.write((int)(x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_7) {
			x -= UPPER_BOUND_6;
			assert (x >>> 48) < (1 << 1);
			os.write((int)(0xFC | x >>> 48));
			os.write((int)(x >>> 40));
			os.write((int)(x >>> 32));
			os.write((int)(x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else if (x < UPPER_BOUND_8) {
			x -= UPPER_BOUND_7;
			os.write(0xFE);
			os.write((int)(x >>> 48));
			os.write((int)(x >>> 40));
			os.write((int)(x >>> 32));
			os.write((int)(x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		} else {
			os.write(0xFF);
			os.write((int)(x >>> 56));
			os.write((int)(x >>> 48));
			os.write((int)(x >>> 40));
			os.write((int)(x >>> 32));
			os.write((int)(x >>> 24));
			os.write((int)(x >>> 16));
			os.write((int)(x >>> 8));
			os.write((int)x);
		}
	}

	/**
	 * Reads a long written by {@link #writeLong(long, OutputStream)}.
	 *
	 * <p>
	 * Note that for efficiency reasons error values returned by {@link InputStream#read()} are ignored.
	 * Thus, when reading past EOF this method will return initially garbage, and then, ultimately,
	 * &minus;1.
	 *
	 * @param is an input stream.
	 * @return the next nonnegative long written by {@link #writeLong(long, OutputStream)}; if the
	 *         result is negative, it is likely reading happened past EOF.
	 *
	 */
	public static long readLong(final InputStream is) throws IOException {
		// Note that alternatively one can switch on Long.numberOfLeadingZeros(~x << 56), but it appears to
		// be slower
		final long x = is.read();
		if (x < 0x80) return x;
		if (x < 0xC0) return ((x & ~0xC0) << 8 | is.read()) + UPPER_BOUND_1;
		if (x < 0xE0) return ((x & ~0xE0) << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_2;
		if (x < 0xF0) return ((x & ~0xF0) << 24 | is.read() << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_3;
		if (x < 0xF8) return ((x & ~0xF8) << 32 | (long)is.read() << 24 | is.read() << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_4;
		if (x < 0xFC) return ((x & ~0xFC) << 40 | (long)is.read() << 32 | (long)is.read() << 24 | is.read() << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_5;
		if (x < 0xFE) return ((x & ~0xFE) << 48 | (long)is.read() << 40 | (long)is.read() << 32 | (long)is.read() << 24 | is.read() << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_6;
		if (x < 0xFF) return ((long)is.read() << 48 | (long)is.read() << 40 | (long)is.read() << 32 | (long)is.read() << 24 | is.read() << 16 | is.read() << 8 | is.read()) + UPPER_BOUND_7;
		return ((long)is.read() << 56 | (long)is.read() << 48 | (long)is.read() << 40 | (long)is.read() << 32 | (long)is.read() << 24 | is.read() << 16 | is.read() << 8 | is.read());
	}

	/**
	 * Writes a byte array by encoding its length using {@link #writeLong(long, OutputStream)}, and then
	 * writing the array.
	 *
	 * @param array a byte array.
	 * @param os an output stream.
	 */
	public static void writeByteArray(final byte[] array, final OutputStream os) throws IOException {
		writeLong(array.length, os);
		os.write(array);
	}

	/**
	 * Reads a byte array written by {@link #writeByteArray(byte[], OutputStream)}.
	 *
	 * @param is an input stream.
	 * @return the next byte array written by {@link #writeByteArray(byte[], OutputStream)}.
	 */
	public static byte[] readByteArray(final InputStream is) throws IOException {
		final long length = readLong(is);
		if (length > Integer.MAX_VALUE) throw new IOException();
		byte[] array = new byte[(int)length];
		if (is.read(array) < array.length) throw new EOFException();
		return array;
	}

	/**
	 * Writes a string by encoding it in UTF-8 and writing the result byte array using
	 * {@link #writeByteArray(byte[], OutputStream)}.
	 *
	 * @param s a string.
	 * @param os an output stream.
	 */
	public static void writeString(final String s, final OutputStream os) throws IOException {
		writeByteArray(s.getBytes(StandardCharsets.UTF_8), os);
	}

	/**
	 * Reads a string written by {@link #writeString(String, OutputStream)}.
	 *
	 * @param is an input stream.
	 * @return the next string written by {@link #writeString(String, OutputStream)}.
	 */
	public static String readString(final InputStream is) throws IOException {
		return new String(readByteArray(is), StandardCharsets.UTF_8);
	}

}
