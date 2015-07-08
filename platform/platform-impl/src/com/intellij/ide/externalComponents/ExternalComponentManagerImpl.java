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
package com.intellij.ide.externalComponents;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Component to keep track of the external components and component sources that can be updated by the updater.
 */
public class ExternalComponentManagerImpl extends ExternalComponentManager {
  private Set<ExternalComponentSource> mySources = Sets.newHashSet();

  @Override
  @NotNull
  public Iterable<ExternalComponentSource> getComponentSources() {
    return mySources;
  }

  @Override
  public void registerComponentSource(@NotNull ExternalComponentSource site) {
    mySources.add(site);
    UpdateSettings updateSettings = UpdateSettings.getInstance();
    List<String> knownSources = updateSettings.getKnownExternalUpdateSources();
    if (!knownSources.contains(site.getName())) {
      knownSources.add(site.getName());
      updateSettings.getEnabledExternalUpdateSources().add(site.getName());
      List<String> channels = site.getAllChannels();
      if (channels != null) {
        updateSettings.getExternalUpdateChannels().put(site.getName(), channels.get(0));
      }
    }
  }

  /**
   * Finds an installed component that could be updated by the given component.
   *
   * @param update The potential update.
   * @param source The source for the update.
   * @return A component from the same source for which the given component is an update, or null if no such component is found.
   */
  @Override
  @Nullable
  public UpdatableExternalComponent findExistingComponentMatching(@NotNull UpdatableExternalComponent update,
                                                                  @NotNull ExternalComponentSource source) {
    Collection<UpdatableExternalComponent> existing = source.getCurrentVersions();
    for (UpdatableExternalComponent c : existing) {
      if (update.isUpdateFor(c)) {
        return c;
      }
    }
    return null;
  }
}
