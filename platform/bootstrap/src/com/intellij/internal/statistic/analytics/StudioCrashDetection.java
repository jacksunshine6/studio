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
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Static utility methods to detect when Android Studio has crashed. */
public class StudioCrashDetection {
  private StudioCrashDetection() {}

  private static final String RECORD_FILE_KEY = "studio.record.file";
  private static final String PLATFORM_PREFIX = "AndroidStudio";

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

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

          String buildVersion = "<unknown>";
          if (buildInfo.exists()) {
            List<String> lines = FileUtil.loadLines(buildInfo);
            if (lines.size() > 0) {
              buildVersion = lines.get(0);
            }
          }
          fw.write(buildVersion);
          fw.write(LINE_SEPARATOR);
          fw.write(System.getProperty("java.runtime.version"));
        } finally {
          fw.close();
        }
      }
    } catch (IOException ex) {
      // continue anyway.
    }
  }

  /** Updates the record created by {@link #start} in this run with the accurate version number. */
  public static void updateRecordedVersionNumber(@NotNull String version) {
      String recordFileName = System.getProperty(RECORD_FILE_KEY);

      if (recordFileName != null) {
        File recordFile = new File(recordFileName);
        try {
          List<String> lines = FileUtil.loadLines(recordFile);
          lines.set(0, version);

          FileWriter fw = new FileWriter(recordFile);
          try {
            for (String line : lines) {
              fw.write(line);
              fw.write(LINE_SEPARATOR);
            }
          } catch (IOException ex) {
            // continue anyway.
          } finally {
            fw.close();
          }
        } catch (IOException ex) {
          // continue anyway.
        }
      }
  }

  /** Deletes the record created by {@link #start} for this run, if it exists. */
  public static void stop() {
    String recordFileName = System.getProperty(RECORD_FILE_KEY);
    if (recordFileName != null) {
      FileUtil.delete(new File(recordFileName));
      System.clearProperty(RECORD_FILE_KEY);
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
