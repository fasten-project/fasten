package eu.fasten.core.legacy;

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

import org.apache.commons.math3.random.RandomGenerator;

import it.unimi.dsi.bits.Fast;

public class FenwickTree {
	private int sum;
	private int n;
	private int[] cumCount;
	
	public FenwickTree(final int n) {
		this.n = n;
		cumCount = new int[n + 1];
		//for (int x = 1; x <= n; x++) incrementCount(x);
	}
	
	public void incrementCount(int x) {
		sum++;

		while (x <= n) {
			cumCount[x]++;
			x += x & -x; // By chance, this gives the right next index 8^).
		}
	}

	public void incrementCount(int x, int c) {
		sum += c;

		while (x <= n) {
			cumCount[x] += c;
			x += x & -x; // By chance, this gives the right next index 8^).
		}
	}

	public int getCount(int x) {
		int c = 0;
		while (x != 0) {
			c += cumCount[x];
			x = x & x - 1; // This cancels out the least nonzero bit.
		}
		return c;
	}

	public int find(int k) {
		int p, q, m;
		p = 0;
		q = 1 << Fast.mostSignificantBit(n);
		while (q != 0) {
			if (p + q <= n) {
				m = cumCount[p + q];
				if (k >= m) {
					p += q;
					k -= m;
				}
			}
			q >>= 1;
		}
		return p;
	}
	
	public int sample(final RandomGenerator random) {
		return find(random.nextInt(sum)) + 1;
	}
}
