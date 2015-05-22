/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.externalComponents.ExternalComponentManager;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

/**
 * @author pti
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private UpdatesSettingsPanel myUpdatesSettingsPanel;
  private boolean myCheckNowEnabled = true;

  public void setCheckNowEnabled(boolean enabled) {
    myCheckNowEnabled = enabled;
  }

  @Override
  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    myUpdatesSettingsPanel.myCheckNow.setVisible(myCheckNowEnabled);
    return myUpdatesSettingsPanel.myPanel;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("updates.settings.title");
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "preferences.updates";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  public void apply() throws ConfigurationException {
    UpdateSettings settings = UpdateSettings.getInstance();

    List<String> enabledExternalUpdateSources = settings.getEnabledExternalUpdateSources();
    Map<String, String> externalUpdateChannels = settings.getExternalUpdateChannels();

    enabledExternalUpdateSources.clear();
    enabledExternalUpdateSources.addAll(myUpdatesSettingsPanel.getEnabledExternalUpdateSources());
    externalUpdateChannels.clear();
    externalUpdateChannels.putAll(myUpdatesSettingsPanel.getExternalUpdateChannels());
    boolean wasEnabled = settings.isCheckNeeded();
    settings.setCheckNeeded(myUpdatesSettingsPanel.myCheckForUpdates.isSelected());
    if (wasEnabled != settings.isCheckNeeded()) {
      UpdateCheckerComponent checker = ApplicationManager.getApplication().getComponent(UpdateCheckerComponent.class);
      if (checker != null) {
        if (wasEnabled) {
          checker.cancelChecks();
        }
        else {
          checker.queueNextCheck();
        }
      }
    }

    settings.setUpdateChannelType(myUpdatesSettingsPanel.getSelectedChannelType().getCode());
    settings.setSecureConnection(myUpdatesSettingsPanel.myUseSecureConnection.isSelected());
  }

  @Override
  public void reset() {
    UpdateSettings settings = UpdateSettings.getInstance();
    myUpdatesSettingsPanel.myCheckForUpdates.setSelected(settings.isCheckNeeded());
    myUpdatesSettingsPanel.myUseSecureConnection.setSelected(settings.isSecureConnection());
    myUpdatesSettingsPanel.updateLastCheckedLabel();
    myUpdatesSettingsPanel.setSelectedChannelType(ChannelStatus.fromCode(settings.getUpdateChannelType()));
    myUpdatesSettingsPanel.setEnabledExternalUpdateSources(settings.getEnabledExternalUpdateSources());
    myUpdatesSettingsPanel.setExternalUpdateChannels(settings.getExternalUpdateChannels());
  }

  @Override
  public boolean isModified() {
    if (myUpdatesSettingsPanel == null) {
      return false;
    }

    UpdateSettings settings = UpdateSettings.getInstance();
    if (settings.isCheckNeeded() != myUpdatesSettingsPanel.myCheckForUpdates.isSelected() ||
        settings.isSecureConnection() != myUpdatesSettingsPanel.myUseSecureConnection.isSelected()) {
      return true;
    }

    List<String> enabledExternal = settings.getEnabledExternalUpdateSources();
    List<String> newEnabledExternal = myUpdatesSettingsPanel.getEnabledExternalUpdateSources();
    if (!enabledExternal.equals(newEnabledExternal)) {
      return true;
    }

    Map<String, String> externalChannels = settings.getExternalUpdateChannels();
    Map<String, String> newExternalChannels = myUpdatesSettingsPanel.getExternalUpdateChannels();
    if (!externalChannels.equals(newExternalChannels)) {
      return true;
    }

    Object channel = myUpdatesSettingsPanel.myUpdateChannels.getSelectedItem();
    return channel != null && !channel.equals(ChannelStatus.fromCode(settings.getUpdateChannelType()));
  }

  @Override
  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  private static class UpdatesSettingsPanel {
    private JPanel myPanel;
    private JButton myCheckNow;
    private JCheckBox myCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;
    private JComboBox myUpdateChannels;
    private JCheckBox myUseSecureConnection;
    private JPanel mySettingsPanel;
    private JPanel myStatusPanel;
    private Map<JCheckBox, ExternalComponentSource> myExternalSourceSettings;
    private Map<ExternalComponentSource, JComboBox> myExternalSourceChannels;


    public UpdatesSettingsPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      String majorVersion = appInfo.getMajorVersion();
      String versionNumber = "";
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        String minorVersion = appInfo.getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          versionNumber = majorVersion + "." + minorVersion;
        }
        else {
          versionNumber = majorVersion + ".0";
        }
      }
      myVersionNumber.setText(appInfo.getVersionName() + " " + versionNumber);
      myBuildNumber.setText(appInfo.getBuild().asString());

      myCheckNow.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myCheckNow));
          UpdateSettings settings = new UpdateSettings();
          settings.loadState(UpdateSettings.getInstance().getState());
          settings.setUpdateChannelType(getSelectedChannelType().getCode());
          settings.setSecureConnection(myUseSecureConnection.isSelected());
          UpdateChecker.updateAndShowResult(project, settings);
          updateLastCheckedLabel();
        }
      });

      LabelTextReplacingUtil.replaceText(myPanel);

      UpdateSettings settings = UpdateSettings.getInstance();
      //noinspection unchecked
      myUpdateChannels.setModel(new CollectionComboBoxModel(ChannelStatus.all(), ChannelStatus.fromCode(settings.getUpdateChannelType())));

      if (!NetUtils.isSniEnabled()) {
        myUseSecureConnection.setEnabled(false);
        boolean tooOld = !SystemInfo.isJavaVersionAtLeast("1.7");
        String message = IdeBundle.message(tooOld ? "update.sni.not.available.notification" : "update.sni.disabled.notification");
        myUseSecureConnection.setToolTipText(message);
      }
    }

    private void updateLastCheckedLabel() {
      long time = UpdateSettings.getInstance().getLastTimeChecked();
      myLastCheckedDate.setText(time == 0 ? IdeBundle.message("updates.last.check.never") : DateFormatUtil.formatPrettyDateTime(time));
    }

    public ChannelStatus getSelectedChannelType() {
      return (ChannelStatus) myUpdateChannels.getSelectedItem();
    }

    public void setSelectedChannelType(ChannelStatus channelType) {
      myUpdateChannels.setSelectedItem(channelType != null ? channelType : ChannelStatus.RELEASE);
    }

    private void createUIComponents() {
      myExternalSourceSettings = Maps.newHashMap();
      myExternalSourceChannels = Maps.newHashMap();
      List<Pair<String, String>> extraStatuses = Lists.newArrayList();

      for (ExternalComponentSource source : ExternalComponentManager.getInstance().getComponentSources()) {
        myExternalSourceSettings.put(new JCheckBox(IdeBundle.message("updates.settings.checkbox") + " " + source.getName()), source);
        List<String> channels = source.getAllChannels();
        if (channels != null) {
          CollectionComboBoxModel model = new CollectionComboBoxModel(channels);
          myExternalSourceChannels.put(source, new JComboBox(model));
        }
        extraStatuses.addAll(source.getStatuses());
      }
      mySettingsPanel = new JPanel(new GridLayoutManager(1 + myExternalSourceSettings.size(), 3));
      mySettingsPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
      myCheckForUpdates = new JCheckBox(IdeBundle.message("updates.settings.checkbox"));
      myCheckForUpdates.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent changeEvent) {
          for (JCheckBox enabled : myExternalSourceSettings.keySet()) {
            enabled.setEnabled(myCheckForUpdates.isSelected());
          }
        }
      });

      myUpdateChannels = new JComboBox();
      int row = 0;
      GridConstraints enabledConstraints =
        new GridConstraints(row, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                            GridConstraints.SIZEPOLICY_FIXED, null, null, null);
      GridConstraints controlConstraints =
        new GridConstraints(row, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW,
                            GridConstraints.SIZEPOLICY_FIXED, null, null, null);

      mySettingsPanel.add(myCheckForUpdates, enabledConstraints);
      mySettingsPanel.add(myUpdateChannels, controlConstraints);
      for (JCheckBox enabledCheckbox : myExternalSourceSettings.keySet()) {
        row++;
        enabledConstraints.setColumn(0);
        enabledConstraints.setRow(row);
        mySettingsPanel.add(enabledCheckbox, enabledConstraints);
        JComboBox channelChooser = myExternalSourceChannels.get(myExternalSourceSettings.get(enabledCheckbox));
        if (channelChooser != null) {
          enabledConstraints.setColumn(1);
          mySettingsPanel.add(channelChooser, enabledConstraints);
        }
      }

      myStatusPanel = new JPanel(new GridLayoutManager(extraStatuses.size() + 3, 2));
      row = 0;
      GridConstraints statusLabelConstraints =
        new GridConstraints(row, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                            GridConstraints.SIZEPOLICY_FIXED, null, null, null);
      GridConstraints statusValueConstraints =
        new GridConstraints(row, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                            GridConstraints.SIZEPOLICY_FIXED, null, null, null);
      myStatusPanel.add(new JLabel(IdeBundle.message("updates.settings.last.check")), statusLabelConstraints);
      myLastCheckedDate = new JLabel();
      myStatusPanel.add(myLastCheckedDate, statusValueConstraints);
      row++;
      statusLabelConstraints.setRow(row);
      statusValueConstraints.setRow(row);
      myStatusPanel.add(new JLabel(IdeBundle.message("updates.settings.current.version")), statusLabelConstraints);
      myVersionNumber = new JLabel();
      myStatusPanel.add(myVersionNumber, statusValueConstraints);
      row++;
      statusLabelConstraints.setRow(row);
      statusValueConstraints.setRow(row);
      myStatusPanel.add(new JLabel(IdeBundle.message("updates.settings.build.number")), statusLabelConstraints);
      myBuildNumber = new JLabel();
      myStatusPanel.add(myBuildNumber, statusValueConstraints);
      for (Pair<String, String> extra : extraStatuses) {
        row++;
        statusLabelConstraints.setRow(row);
        statusValueConstraints.setRow(row);
        myStatusPanel.add(new JLabel(extra.first), statusLabelConstraints);
        myStatusPanel.add(new JLabel(extra.second), statusValueConstraints);
      }
    }

    public void setEnabledExternalUpdateSources(List<String> enabledExternalUpdateSources) {
      for (JCheckBox enabled : myExternalSourceSettings.keySet()) {
        enabled.setSelected(enabledExternalUpdateSources.contains(myExternalSourceSettings.get(enabled).getName()));
      }
    }

    public List<String> getEnabledExternalUpdateSources() {
      List<String> result = Lists.newArrayList();
      for (JCheckBox enabled : myExternalSourceSettings.keySet()) {
        if (enabled.isSelected()) {
          result.add(myExternalSourceSettings.get(enabled).getName());
        }
      }
      return result;
    }

    public void setExternalUpdateChannels(@NotNull Map<String, String> enabledExternalUpdateSources) {
      for (ExternalComponentSource source : ExternalComponentManager.getInstance().getComponentSources()) {
        String sourceName = source.getName();
        String channelName = enabledExternalUpdateSources.get(sourceName);
        JComboBox channelSelector = myExternalSourceChannels.get(source);
        if (channelName != null && channelSelector != null) {
          channelSelector.setSelectedItem(channelName);
        }
      }
    }

    @NotNull
    public Map<String, String> getExternalUpdateChannels() {
      Map<String, String> result = Maps.newHashMap();
      for (ExternalComponentSource source : ExternalComponentManager.getInstance().getComponentSources()) {
        String channel = ChannelStatus.RELEASE.getDisplayName();
        if (myExternalSourceChannels.containsKey(source)) {
          channel = (String)myExternalSourceChannels.get(source).getSelectedItem();
        }
        result.put(source.getName(), channel);
      }
      return result;
    }
  }
}
