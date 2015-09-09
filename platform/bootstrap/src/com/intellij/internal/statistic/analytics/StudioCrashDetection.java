/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.analytics;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Static utility methods to detect when Android Studio has crashed. */
public class StudioCrashDetection {
  private StudioCrashDetection() {}

  private static final String RECORD_FILE_KEY = "studio.record.file";
  private static final String PLATFORM_PREFIX = "AndroidStudio";

  /**
   * Creates a record of the application starting, unique to this run.
   *
   * @throws AssertionError if called more than once per run
   */
  public static void start() {
    if (System.getProperty(RECORD_FILE_KEY) != null) throw new AssertionError("StudioCrashDetection.start called more than once");
    try {
      File f = new File(PathManager.getTempPath(),
                        String.format("%s.%s", PLATFORM_PREFIX, UUID.randomUUID().toString()));
      if (f.createNewFile()) {
        // We use a system property to pass the filename across classloaders.
        System.setProperty(RECORD_FILE_KEY, f.getAbsolutePath());

        FileWriter fw = new FileWriter(f);
        try {
          File buildInfo = new File(PathManager.getHomePath(), "build.txt");
          if (!buildInfo.exists() && SystemInfo.isMac) {
            // On a Mac, also try to find it under Resources.
            buildInfo = new File(PathManager.getHomePath(), "Resources/build.txt");
          }

          if (buildInfo.exists()) {
            fw.write(FileUtil.loadFile(buildInfo));
          }
          fw.write(System.getProperty("java.runtime.version"));
        } finally {
          fw.close();
        }
      }
    } catch (IOException ignored) {}
  }

  /** Deletes the record created by {@link #start} for this run, if it exists. */
  public static void stop() {
    String recordFileName = System.getProperty(RECORD_FILE_KEY);
    if (recordFileName != null) {
      FileUtil.delete(new File(recordFileName));
    }
  }

  /** Returns and deletes any records created by {@link #start} in previous runs. */
  public static List<String> reapCrashDescriptions() {
    File[] previousRecords = new File(PathManager.getTempPath()).listFiles(
      new FileFilter() {
        final String recordFile = System.getProperty(RECORD_FILE_KEY);
        @Override public boolean accept(File pathname) {
          return pathname.getName().startsWith(PLATFORM_PREFIX) && !pathname.getAbsolutePath().equals(recordFile);
        }
      });
    ArrayList<String> descriptions = new ArrayList<String>();
    if (previousRecords != null) {
      for (File record : previousRecords) {
        try {
          descriptions.add(FileUtil.loadFile(record));
        } catch (IOException ex) {
          descriptions.add("<unknown>");
        }
        FileUtil.delete(record);
      }
    }
    return descriptions;
  }
}
