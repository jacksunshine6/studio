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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * This class mirrors some function of the Android plugin's UsageTracker class.
 * It is intended to be used from the platform, which cannot have any dependencies on the android plugin.
 */
public class PlatformUsageTracker {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  // Tracking is enabled in internal builds
  private static final boolean DEBUG = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  @NonNls private static final String ANALYTICS_URL = "https://ssl.google-analytics.com/collect";
  @NonNls private static final String ANAYLTICS_ID = "UA-44790371-1";
  @NonNls private static final String ANALYTICS_APP = "Android Studio";

  private static final List<? extends NameValuePair> analyticsBaseData = ImmutableList
    .of(new BasicNameValuePair("v", "1"),
        new BasicNameValuePair("tid", ANAYLTICS_ID),
        new BasicNameValuePair("an", ANALYTICS_APP),
        new BasicNameValuePair("av", UNIT_TEST_MODE ? "unit-test" : ApplicationInfo.getInstance().getFullVersion()),
        new BasicNameValuePair("cid", UNIT_TEST_MODE ? "unit-test" : UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())));

  public static boolean trackingEnabled() {
    return DEBUG || StatisticsUploadAssistant.isSendAllowed();
  }

  public static void trackException(@NotNull Throwable t, boolean fatal) {
    if (!DEBUG && !trackingEnabled()) {
      return;
    }

    t = getRootCause(t);
    post(ImmutableList.of(new BasicNameValuePair("t", "exception"), new BasicNameValuePair("exd", getDescription(t)),
                          new BasicNameValuePair("exf", fatal ? "1" : "0")));
  }

  public static void trackActivity(long count) {
    if (!DEBUG && !trackingEnabled()) {
      return;
    }

    post(ImmutableList.of(new BasicNameValuePair("t", "event"),
                          new BasicNameValuePair("ec", "ActivityTracker"),
                          new BasicNameValuePair("ea", "Hit"),
                          new BasicNameValuePair("ev", Long.toString(count)),
                          new BasicNameValuePair("cm1", Long.toString(count))));
  }

  private static void post(@NotNull final List<BasicNameValuePair> parameters) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(ANALYTICS_URL);
        try {
          request.setEntity(new UrlEncodedFormEntity(Iterables.concat(analyticsBaseData, parameters)));
          HttpResponse response = client.execute(request);
          StatusLine status = response.getStatusLine();
          HttpEntity entity = response.getEntity(); // throw it away, don't care, not sure if we need to read in the response?
          if (status.getStatusCode() >= 300) {
            // something went wrong, fail quietly, we probably have to diagnose analytics errors on our side
            // usually analytics accepts ANY request and always returns 200
            // we don't want to call logging methods here
            if (DEBUG) {
              System.err.println("Error reporting to Analytics, return code: " + status.getStatusCode());
            }
          }
        }
        catch (IOException e) {
          // something went wrong, fail quietly
          // we don't want to call logging methods here
          if (DEBUG) {
            System.err.println("Error reporting to Analytics: " + e.getMessage());
          }
        }
        finally {
          HttpClientUtils.closeQuietly(client);
        }
      }
    });
  }

  /**
   * Returns the description corresponding to a throwable suitable for consumption by GA measurement protocol.
   * The description cannot include PII, and should be < 150 bytes.
   */
  @VisibleForTesting
  @NotNull
  public static String getDescription(@NotNull Throwable t) {
    boolean isAndroid = false;
    for (StackTraceElement el : t.getStackTrace()) {
      if (el.getClassName().contains("android")) {
        isAndroid = true;
        break;
      }
    }

    String sourceLocation = "";
    if (t.getStackTrace().length > 0) {
      StackTraceElement loc = t.getStackTrace()[0];
      sourceLocation = " @ " + loc.getFileName() + ":" + loc.getLineNumber();
    }
    String prefix = isAndroid ? "android:" : "";
    String desc = prefix + t.getClass().getSimpleName() + sourceLocation;

    if (desc.length() > 150) {
      desc = desc.substring(0, 150); // quick hack: lets assume this is mostly ASCII
    }

    return desc;
  }

  // Similar to ExceptionUntil.getRootCause, but attempts to avoid infinite recursion
  private static Throwable getRootCause(Throwable e) {
    int depth = 0;
    while (depth++ < 20) {
      if (e.getCause() == null) return e;
      e = e.getCause();
    }
    return e;
  }
}
