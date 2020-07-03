package eu.fasten.core.data;

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

import java.io.File;
import java.io.IOException;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.sux4j.mph.GOV3Function;
import it.unimi.dsi.sux4j.mph.Hashes;

/**
 * A specialized subclass of {@link it.unimi.dsi.sux4j.mph.GOV3Function} for {@link Long} data that
 * provides a fast {@link #getLong(long)} method (bypassing the {@linkplain TransformationStrategy
 * transformation strategy}).
 *
 * <p>
 * The specialized {@linkplain GOV3LongFunction.Builder builder} makes it possible to set only the key
 * iterable and, optionally, a temporary directory. Each key will be mapped in its ordinal position
 * by the resulting map.
 */

public class GOV3LongFunction extends GOV3Function<Long> {
	private static final long serialVersionUID = 0L;

	/** A builder class for {@link GOV3LongFunction}. */
	public static class Builder {
		protected Iterable<? extends Long> keys;
		protected File tempDir;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/**
		 * Specifies the keys of the function.
		 *
		 * @param keys the keys of the function.
		 * @return this builder.
		 */
		public Builder keys(final Iterable<? extends Long> keys) {
			this.keys = keys;
			return this;
		}

		/**
		 * Specifies a temporary directory for the {@link BucketedHashStore}.
		 *
		 * @param tempDir a temporary directory for the {@link BucketedHashStore} files, or {@code null} for
		 *            the standard temporary directory.
		 * @return this builder.
		 */
		public Builder tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/**
		 * Builds a new function.
		 *
		 * @return a {@link GOV3LongFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public GOV3LongFunction build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			built = true;
			return new GOV3LongFunction(keys, tempDir);
		}
	}

	protected GOV3LongFunction(final Iterable<? extends Long> keys, final File tempDir) throws IOException {
		super(keys, TransformationStrategies.rawFixedLong(), 0, null, -1, false, tempDir, null, false);
	}

	// This must be kept in sync with it.unimi.dsi.sux4j.mph.Hashes.ARBITRARY_BITS, until the public
	// release makes the field public.
	private final static long ARBITRARY_BITS = 0x9e3779b97f4a7c13L;

	/**
	 * A specialized replica of {@link Hashes#spooky4(it.unimi.dsi.bits.BitVector, long, long[])} that
	 * by passes the {@linkplain TransformationStrategy transformation strategy}.
	 */
	protected static void spooky4(final long l, final long seed, final long[] tuple) {
		long h0, h1, h2, h3;
		h0 = seed;
		h1 = seed;
		h2 = ARBITRARY_BITS;
		h3 = ARBITRARY_BITS;

		h2 += l;

		h0 += Long.SIZE;

		h3 ^= h2;
		h2 = Long.rotateLeft(h2, 15);
		h3 += h2;
		h0 ^= h3;
		h3 = Long.rotateLeft(h3, 52);
		h0 += h3;
		h1 ^= h0;
		h0 = Long.rotateLeft(h0, 26);
		h1 += h0;
		h2 ^= h1;
		h1 = Long.rotateLeft(h1, 51);
		h2 += h1;
		h3 ^= h2;
		h2 = Long.rotateLeft(h2, 28);
		h3 += h2;
		h0 ^= h3;
		h3 = Long.rotateLeft(h3, 9);
		h0 += h3;
		h1 ^= h0;
		h0 = Long.rotateLeft(h0, 47);
		h1 += h0;
		h2 ^= h1;
		h1 = Long.rotateLeft(h1, 54);
		h2 += h1;
		h3 ^= h2;
		h2 = Long.rotateLeft(h2, 32);
		h3 += h2;
		h0 ^= h3;
		h3 = Long.rotateLeft(h3, 25);
		h0 += h3;
		h1 ^= h0;
		h0 = Long.rotateLeft(h0, 63);
		h1 += h0;

		tuple[1] = h1;
		tuple[0] = h0;
	}

	public long getLong(final long l) {
		final long[] signature = new long[2];
		spooky4(l, globalSeed, signature);
		return getLongBySignature(signature);
	}
}
