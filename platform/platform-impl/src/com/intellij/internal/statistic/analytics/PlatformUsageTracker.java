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

import com.intellij.ide.SystemHealthMonitor;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlatformUsageTracker {
  private static final ExtensionPointName<PlatformUsageUploader> EP_NAME = ExtensionPointName.create("com.intellij.usageUploader");
  private static PlatformUsageTracker ourInstance;

  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  private static final boolean DEBUG = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  @Nullable private final PlatformUsageUploader myUploader;

  public synchronized static PlatformUsageTracker getInstance() {
    if (ourInstance == null) {
      ourInstance = new PlatformUsageTracker();
    }
    return ourInstance;
  }

  private PlatformUsageTracker() {
    PlatformUsageUploader[] uploaders = EP_NAME.getExtensions();
    myUploader = uploaders.length > 0 ? uploaders[0] : null;
  }

  public boolean trackingEnabled() {
    return DEBUG || ((StatisticsUploadAssistant.isSendAllowed()) && (myUploader != null));
  }

  public void trackCrash(@NotNull String description) {
    if (trackingEnabled() && myUploader != null) {
      myUploader.trackCrash(description);
    }
  }

  public void trackException(@NotNull Throwable t, boolean fatal) {
    if (trackingEnabled() && myUploader != null) {
      SystemHealthMonitor.incrementAndSaveExceptionCount();
      myUploader.trackException(t, fatal);
    }
  }

  public void trackExceptionsAndActivity(final long activityCount, final long exceptionCount, final long fatalExceptionCount) {
    if (trackingEnabled() && myUploader != null) {
      myUploader.trackExceptionsAndActivity(activityCount, exceptionCount, fatalExceptionCount);
    }
  }
}
