/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.connect.StatisticsServiceEP;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.notification.NotificationsManager;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SendStatisticsComponent implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance(SendStatisticsComponent.class);

  private static final int DELAY_IN_MIN = 10;

  private final Alarm myAlarm;

  public SendStatisticsComponent() {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());

    // Ensure that the notification manager (also an application component) is registered first;
    // otherwise this component's initComponent() call will fire a notification event bus
    // to show the opt-in dialog, but the notification component may not yet have been initialized
    // and is therefore not subscribed and listening.
    NotificationsManager.getNotificationsManager();

    NotificationsConfigurationImpl.remove("SendUsagesStatistics");
    NotificationsConfiguration.getNotificationsConfiguration().register(
      StatisticsNotificationManager.GROUP_DISPLAY_ID,
      NotificationDisplayType.STICKY_BALLOON,
      false);
  }

  private void runStatisticsService() {
    StatisticsService statisticsService = StatisticsUploadAssistant.getStatisticsService();

    if (StatisticsUploadAssistant.showNotification()) {
      StatisticsNotificationManager.showNotification(statisticsService);
    }
    else if (StatisticsUploadAssistant.isSendAllowed() && StatisticsUploadAssistant.isTimeToSend()) {
      runWithDelay(statisticsService);
    }
  }

  private void runWithDelay(final @NotNull StatisticsService statisticsService) {
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        statisticsService.send();
      }
    }, DELAY_IN_MIN * 60 * 1000);
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    runStatisticsService();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return SendStatisticsComponent.class.getName();
  }
}
