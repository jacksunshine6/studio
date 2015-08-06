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
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.Locale;

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

  // GA automatically detects the OS from the browser user agent. It is not very clear if it can parse some random UA string,
  //
  // Wikipedia reports that the format is typically:
  //    Mozilla/[version] ([system and browser information]) [platform] ([platform details]) [extensions]
  // Chrome for example uses:
  //    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.2 Safari/537.36"
  // We'll use something like the following:
  //    Studio/1.4.0.0 (Linux; U; Linux 3.13.0-57-generic; en-us)
  @NonNls private static final String ANALYTICS_UA = String.format(Locale.US, "Studio/%1$s (%2$s; U; %2$s %3$s; %4$s)",
                                                                   UNIT_TEST_MODE ? "u" : ApplicationInfo.getInstance().getStrictVersion(),
                                                                   SystemInfo.OS_NAME,
                                                                   SystemInfo.OS_VERSION,
                                                                   getLanguage());

  private static final int MAX_DESCRIPTION_SIZE = 150; // max allowed by GA

  private static final List<? extends NameValuePair> analyticsBaseData = ImmutableList
    .of(new BasicNameValuePair("v", "1"),
        new BasicNameValuePair("tid", ANAYLTICS_ID),
        new BasicNameValuePair("an", ANALYTICS_APP),
        new BasicNameValuePair("av", UNIT_TEST_MODE ? "unit-test" : ApplicationInfo.getInstance().getStrictVersion()),
        new BasicNameValuePair("cid", UNIT_TEST_MODE ? "unit-test" : UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())));

  private static String getLanguage() {
    Locale locale = Locale.getDefault();
    if (locale == null) {
      return "";
    }

    String language = locale.getLanguage();
    if (language == null) {
      return "";
    }

    String country = locale.getCountry();
    return country == null ? language.toLowerCase(Locale.US) : language.toLowerCase(Locale.US) + "-" + country.toLowerCase(Locale.US);
  }

  public static boolean trackingEnabled() {
    return DEBUG || StatisticsUploadAssistant.isSendAllowed();
  }

  public static void trackCrash(@NotNull String description) {
    if (!DEBUG && !trackingEnabled()) {
      return;
    }

    post(ImmutableList.of(new BasicNameValuePair("t", "exception"),
                          new BasicNameValuePair("exd", description),
                          new BasicNameValuePair("exf", "1")));
  }

  public static void trackException(@NotNull Throwable t, boolean fatal) {
    if (!DEBUG && !trackingEnabled()) {
      return;
    }

    t = getRootCause(t);
    post(ImmutableList.of(new BasicNameValuePair("t", "exception"),
                          new BasicNameValuePair("exd", getDescription(t)),
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
          request.setHeader("User-Agent", ANALYTICS_UA);
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
    StringBuilder sb = new StringBuilder(MAX_DESCRIPTION_SIZE);
    String simpleName = t.getClass().getSimpleName().replace("Exception", "Ex").replace("Error", "Er");
    sb.append(simpleName);

    StackTraceElement[] stackTraceElements = t.getStackTrace();
    if (stackTraceElements.length > 0) {
      sb.append(" @ ");
    }

    boolean androidPlugin = false;
    String lastFileName = "";

    int i;
    for (i = 0; i < stackTraceElements.length && sb.length() < MAX_DESCRIPTION_SIZE; i++) {
      StackTraceElement el = stackTraceElements[i];

      // skip java[x].* packages
      String className = el.getClassName();
      if (className != null && (className.startsWith("java.") || className.startsWith("javax."))) {
        sb.append('.');
        continue;
      }

      if (i != 0) {
        sb.append(" < ");
      }

      String fileName = getBaseName(el.getFileName());
      // skip filename if it is the same as the previous stack element
      if (!StringUtil.equals(fileName, lastFileName)) {
        sb.append(fileName);
        lastFileName = fileName;
      }

      sb.append(':');
      sb.append(el.getLineNumber());

      // track whether we've included an element from the android plugin
      if (!androidPlugin) {
        androidPlugin = fromAndroidPlugin(el);
      }
    }

    String desc = sb.toString();

    // if we have not included an android plugin in the description so far, then let's check to see if one should be..
    if (!androidPlugin && i < stackTraceElements.length) {
      for (; i < stackTraceElements.length; i++) {
        StackTraceElement el = stackTraceElements[i];
        if (fromAndroidPlugin(el)) {
          String android = "... < " + el.getFileName() + ":" + el.getLineNumber();
          if (desc.length() + android.length() > MAX_DESCRIPTION_SIZE) {
            desc = desc.substring(0, MAX_DESCRIPTION_SIZE - android.length());
          }
          desc += android;
          break;
        }
      }
    }

    // make sure the description is within size limits
    if (desc.length() > MAX_DESCRIPTION_SIZE) {
      desc = desc.substring(0, MAX_DESCRIPTION_SIZE - 1) + ">";
    }

    // most likely all file names are ASCII, so this should be unnecessary, but lets be safe
    while (desc.getBytes(Charsets.UTF_8).length > MAX_DESCRIPTION_SIZE) {
      desc = desc.substring(0, desc.length() - 1);
    }

    return desc;
  }

  private static boolean fromAndroidPlugin(StackTraceElement el) {
    return el.getClassName().contains("android");
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

  private static String getBaseName(@NotNull String fileName) {
    int extension = fileName.indexOf('.');
    if (extension > 0) {
      return fileName.substring(0, extension);
    } else {
      return fileName;
    }
  }
}
