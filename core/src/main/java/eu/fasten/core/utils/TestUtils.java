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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class TestUtils {
    public static File getTestResource(String path) {
        File file = null;
        URL resource = Thread.currentThread().getContextClassLoader().getResource(path);
		if(resource == null) {
			throw new IllegalArgumentException("test resource not found: " + path);
		}
        try {
            file = new File(new URI(resource.toString()).getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return file;
    }
}
