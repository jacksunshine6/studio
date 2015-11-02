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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.ide.SystemHealthMonitor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Note: Methods such as trackException() are called from within the logger when an exception is detected,
// so don't use a Logger in this class and possibly end up in an infinite recursion.
// Just use System.err.print if we are in DEBUG mode, otherwise don't log anything
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class AnalyticsUploader {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;

  // Tracking is enabled in internal builds
  private static final boolean DEBUG = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  @NonNls private static final String ANALYTICS_URL = "https://ssl.google-analytics.com/collect";
  @NonNls private static final String ANALYTICS_ID = DEBUG ? "UA-44790371-1" : "UA-19996407-3";
  @NonNls private static final String ANALYTICS_APP = "Android Studio";
  private static final String INSTALLATION_ID =
    UNIT_TEST_MODE ? "unit-test" : UpdateChecker.getInstallationUID(PropertiesComponent.getInstance());

  private static final int MAX_DESCRIPTION_SIZE = 150; // max allowed by GA

  private static final String PROTOCOL_VERSION = "v";
  private static final String TRACKING_ID = "tid";
  private static final String APPLICATION_NAME = "an";
  private static final String APPLICATION_VERSION = "av";
  private static final String CLIENT_ID = "cid";
  private static final String HIT_TYPE = "t";

  private static final String HIT_TYPE_EVENT = "event";
  private static final String EVENT_CATEGORY = "ec";
  private static final String EVENT_ACTION = "ea";
  private static final String EVENT_LABEL = "el";
  private static final String EVENT_VALUE = "ev";

  private static final String HIT_TYPE_EXCEPTION = "exception";
  private static final String EXCEPTION_DESCRIPTION = "exd";
  private static final String EXCEPTION_FATAL = "exf";

  // Custom dimensions should match the index given to them in Analytics
  // See https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#customs
  private static final String CD_OS_NAME = "cd1";
  private static final String CD_OS_VERSION = "cd2";
  private static final String CD_JAVA_RUNTIME_VERSION = "cd3";
  private static final String CD_UPDATE_CHANNEL = "cd4";
  private static final String CD_LOCALE = "cd5";

  // Custom metrics should match the index given to them in Analytics
  private static final String CM_ACTIVITY_COUNT = "cm1";

  private static final List<BasicNameValuePair> analyticsBaseData = ImmutableList
    .of(new BasicNameValuePair(PROTOCOL_VERSION, "1"),
        new BasicNameValuePair(TRACKING_ID, ANALYTICS_ID),
        new BasicNameValuePair(APPLICATION_NAME, ANALYTICS_APP),
        new BasicNameValuePair(APPLICATION_VERSION, UNIT_TEST_MODE ? "unit-test" : ApplicationInfo.getInstance().getStrictVersion()),
        new BasicNameValuePair(CLIENT_ID, INSTALLATION_ID),
        new BasicNameValuePair(CD_OS_NAME, SystemInfo.OS_NAME),
        new BasicNameValuePair(CD_OS_VERSION, SystemInfo.OS_VERSION),
        new BasicNameValuePair(CD_JAVA_RUNTIME_VERSION, SystemInfo.JAVA_RUNTIME_VERSION),
        new BasicNameValuePair(CD_LOCALE, getLanguage()));
  public static final String CATEGORY_STUDIO_EXCEPTION = "excstudio";

  private static final LastSeenExceptions ourLastSeenExceptions = new LastSeenExceptions(3);

  public static String getLanguage() {
    Locale locale = Locale.getDefault();
    return locale == null ? "unknown" : locale.toString();
  }

  public static boolean trackingEnabled() {
    return DEBUG || StatisticsUploadAssistant.isSendAllowed();
  }

  public static void trackEvent(@NotNull String eventCategory,
                         @NotNull String eventAction,
                         @Nullable String eventLabel,
                         @Nullable Integer eventValue) {
    List<BasicNameValuePair> postData = Lists.newArrayList(analyticsBaseData);

    postData.add(new BasicNameValuePair(HIT_TYPE, HIT_TYPE_EVENT));
    postData.add(new BasicNameValuePair(EVENT_CATEGORY, eventCategory));
    postData.add(new BasicNameValuePair(EVENT_ACTION, eventAction));
    if(!StringUtil.isEmpty(eventLabel)) {
      postData.add(new BasicNameValuePair(EVENT_LABEL, eventLabel));
    }
    if(eventValue != null) {
      if(eventValue < 0 && DEBUG) {
        System.err.println("Attempting to send negative event value to the analytics server");
      } else {
        postData.add(new BasicNameValuePair(EVENT_VALUE, eventValue.toString()));
      }
    }
    postToAnalytics(postData);
  }

  public static void trackCrashes(@NotNull Iterable<String> descriptions) {
    long crashCount = 0;
    for (String description : descriptions) {
      trackCrash(description);
      ++crashCount;
    }
    trackExceptionsAndActivity(0, 0, crashCount);
  }

  private static void trackCrash(@NotNull String description) {
    if (!trackingEnabled()) {
      return;
    }

    try {
      postToAnalytics(ImmutableList.of(new BasicNameValuePair(HIT_TYPE, HIT_TYPE_EXCEPTION),
                                       new BasicNameValuePair(EXCEPTION_DESCRIPTION, "StudioCrash: " + description),
                                       new BasicNameValuePair(EXCEPTION_FATAL, "1")));
    } catch (Throwable throwable) {
      if (DEBUG) {
        System.err.println("Unexpected error while reporting a crash: " + throwable);
      }
    }
  }

  public static void trackException(@NotNull Throwable t, boolean fatal) {
    if (!trackingEnabled()) {
      return;
    }

    try {
      t = getRootCause(t);
      if (t instanceof Logger.EmptyThrowable) {
        return;
      }

      SystemHealthMonitor.incrementAndSaveExceptionCount();

      String description = getDescription(t);
      postToAnalytics(ImmutableList.of(new BasicNameValuePair(HIT_TYPE, HIT_TYPE_EXCEPTION),
                                       new BasicNameValuePair(EXCEPTION_DESCRIPTION, description),
                                       new BasicNameValuePair(EXCEPTION_FATAL, fatal ? "1" : "0")));

      ourLastSeenExceptions.add(description);
    } catch (Throwable throwable) {
      if (DEBUG) {
        System.err.println("Unexpected error while reporting a crash: " + throwable);
      }
    }
  }

  @NotNull
  public static String getLastExceptionDescription() {
    return ourLastSeenExceptions.getDescriptions();
  }

  public static void trackExceptionsAndActivity(final long activityCount, final long exceptionCount, final long fatalExceptionCount) {
    if (!trackingEnabled()) {
      return;
    }

    // send activity count to GA
    try {
      // @formatter:off
      postToAnalytics(ImmutableList.of(new BasicNameValuePair(HIT_TYPE, HIT_TYPE_EVENT),
                                       new BasicNameValuePair(EVENT_CATEGORY, "ActivityTracker"),
                                       new BasicNameValuePair(EVENT_ACTION, "Hit"),
                                       new BasicNameValuePair(EVENT_VALUE, Long.toString(activityCount)),
                                       new BasicNameValuePair(CM_ACTIVITY_COUNT, Long.toString(activityCount))));
      // @formatter:on
    } catch (Throwable throwable) {
      if (DEBUG) {
        System.err.println("Unexpected error while reporting a activity: " + throwable);
      }
    }

    // send all the counters to tools.google.com
    if (!ApplicationManager.getApplication().isInternal()) {
      // @formatter:off
      postToGoogleLogs(CATEGORY_STUDIO_EXCEPTION, ImmutableMap.of("activity", Long.toString(activityCount),
                                                                  "exc", Long.toString(exceptionCount),
                                                                  "exf", Long.toString(fatalExceptionCount)));
      // @formatter:on
    }
  }

  public static void postToGoogleLogs(@NotNull final String categoryId, @NotNull final Map<String, String> parameters) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          URL url = getPingUrl(categoryId, ApplicationInfo.getInstance().getStrictVersion(), INSTALLATION_ID, parameters);
          postToGoogleLogs(url);
        }
        catch (Throwable t) {
          if (DEBUG) {
            System.err.println("Unexpected error while uploading exception metrics: " + t);
          }
        }
      }
    });
  }

  // Posting to Dremel means simply connecting to the URL and ignoring the response
  private static void postToGoogleLogs(@NotNull URL url) throws IOException {
    // Discard the actual response, but make sure it reads OK
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();

    int responseCode;
    try {
      responseCode = conn.getResponseCode();
    }
    catch (UnknownHostException e) {
      if (DEBUG) {
        System.err.println("Unexpected exception while sending exception counts: " + e);
      }
      return;
    }
    finally {
      conn.disconnect();
    }

    // tools.google.com is expected to return a 404
    if (DEBUG && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
      System.err.println("Ping did not return a 404");
    }
  }

  private static void postToAnalytics(@NotNull final List<BasicNameValuePair> parameters) {
    String channel = UNIT_TEST_MODE ?
                     "unit-test" : ChannelStatus.fromCode(UpdateSettings.getInstance().getUpdateChannelType()).getDisplayName();
    final List<BasicNameValuePair> runtimeData = Collections.singletonList(new BasicNameValuePair(CD_UPDATE_CHANNEL, channel));

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(ANALYTICS_URL);
        try {
          request.setEntity(new UrlEncodedFormEntity(Iterables.concat(analyticsBaseData, runtimeData, parameters)));
          HttpResponse response = client.execute(request);
          StatusLine status = response.getStatusLine();
          @SuppressWarnings("unused")
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
          String android = "... < " + getBaseName(el.getFileName()) + ":" + el.getLineNumber();
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

  private static String getBaseName(@Nullable String fileName) {
    if (Strings.isNullOrEmpty(fileName)) {
      return "U";
    }

    int extension = fileName.indexOf('.');
    if (extension > 0) {
      return fileName.substring(0, extension);
    } else {
      return fileName;
    }
  }

  @VisibleForTesting
  @NotNull
  public static URL getPingUrl(@NotNull String categoryId,
                               @NotNull String version,
                               @NotNull String installationId,
                               @NotNull Map<String, String> parameters) throws MalformedURLException, UnsupportedEncodingException {
    String urlPrefix = String.format(Locale.US,
                             "https://tools.google.com/service/update?as=androidsdk_%1$s&version=%2$s&uid=%3$s",
                             categoryId,
                             URLEncoder.encode(version, Charsets.UTF_8.name()),
                             URLEncoder.encode(installationId, Charsets.UTF_8.name()));

    StringBuilder sb = new StringBuilder(urlPrefix);
    for (String key : parameters.keySet()) {
      String value = parameters.get(key);
      sb.append('&');
      sb.append(URLEncoder.encode(key, Charsets.UTF_8.name()));
      sb.append('=');
      sb.append(URLEncoder.encode(value, Charsets.UTF_8.name()));
    }

    return new URL(sb.toString());
  }
}
