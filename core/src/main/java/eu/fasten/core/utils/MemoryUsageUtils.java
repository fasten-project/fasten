/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryUsageUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryUsageUtils.class);
    private static final double MEGA = 1024.0 * 1024.0;

    public static void logMemoryUsage() {
        System.gc();

        var max = Runtime.getRuntime().maxMemory() / MEGA;
        var curMax = Runtime.getRuntime().totalMemory() / MEGA;
        var curFree = Runtime.getRuntime().freeMemory() / MEGA;
        var used = curMax - curFree;

        LOG.info("Used memory: {} MB", used);
        LOG.info("Heap Size: {} MB (max: {} MB)", curMax, max);
    }

    public static void logMaxMemory() {
        var mm = Runtime.getRuntime().maxMemory() / MEGA;
        LOG.info("Max memory: {} MB", mm);
    }
}