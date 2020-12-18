package eu.fasten.core.legacy;


import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.jupiter.api.Test;

import eu.fasten.core.legacy.FenwickTree;


public class FenwickTreeTest {

    @Test
    public void testBernoulli() {
        FenwickTree t = new FenwickTree(2);
        RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
        // Increment the same number of times counters 1 and 2
        int times = random.nextInt(1000) + 1000;
        int[] increments = new int[]{times, times};
        while (increments[0] + increments[1] > 0) {
            int whichOne = random.nextInt(2);
            if (increments[whichOne] == 0) whichOne = 1 - whichOne;
            t.incrementCount(whichOne + 1);
            increments[whichOne]--;
        }
        // Now sample
        int sampleSize = 1000000;
        for (int i = 0; i < sampleSize; i++) {
            int sample = t.sample(random);
            increments[sample - 1]++;
        }
        assertEquals((double) increments[0] / sampleSize, 0.5, 1E-3);
    }


    @Test
    public void testBernoulli2() {
        FenwickTree t = new FenwickTree(2);
        RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
        t.incrementCount(1);
        t.incrementCount(2);
        t.incrementCount(2);

        // Now sample
        int sampleSize = 10000000;

        int c = 0, d = 0;
        for (int i = 0; i < sampleSize; i++) {
            int sample = t.sample(random);
            if (sample == 1) c++;
            if (sample == 2) d++;
        }
        assertEquals(1. / 3, (double) c / sampleSize, 1E-3);
        assertEquals(2. / 3, (double) d / sampleSize, 1E-3);
    }


    @Test
    public void testDistr() {
        RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
        int n = 2 + random.nextInt(5);
        FenwickTree t = new FenwickTree(n);
        int[] increments = new int[n];
        int[] intended = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            increments[i] = random.nextInt(10000) + 1;
            intended[i] = increments[i];
            total += increments[i];
        }
        for (int i = 0; i < total; i++) {
            int whichOne = random.nextInt(n);
            if (increments[whichOne] == 0) {
                i--;
                continue;
            }
            t.incrementCount(whichOne + 1);
            increments[whichOne]--;
        }

        for (int i = 1; i <= n; i++)
            assertEquals(intended[i - 1], t.getCount(i) - t.getCount(i - 1), i + "");
        // Now sample
        int[] sampled = new int[n];
        int sampleSize = 10000000;
        for (int i = 0; i < sampleSize; i++) {
            int sample = t.sample(random);
            sampled[sample - 1]++;
        }
        for (int i = 0; i < n; i++)
            assertEquals((double) sampled[i] / sampleSize, (double) intended[i] / total, 1E-3);
    }

}
