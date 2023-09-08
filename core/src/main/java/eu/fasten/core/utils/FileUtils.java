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

import java.io.File;
import java.io.IOException;

public class FileUtils {

    /**
     * Force-deletes the file or directory.
     *
     * @param file File to be deleted
     */
    public static void forceDeleteFile(File file) {
        if (file == null) {
            return;
        }
        try {
            org.apache.commons.io.FileUtils.forceDelete(file);
        } catch (IOException ignored) {} finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }
}