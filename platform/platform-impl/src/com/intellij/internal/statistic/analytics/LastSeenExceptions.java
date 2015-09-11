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

import com.google.common.base.Joiner;
import org.jetbrains.annotations.NotNull;

public class LastSeenExceptions {
  private int myIndex = 0;
  private final int mySize;
  private final String[] myThrowableDescriptions;

  public LastSeenExceptions(int max) {
    mySize = max;
    myThrowableDescriptions = new String[max];
  }

  public synchronized void add(@NotNull String desc) {
    myThrowableDescriptions[myIndex] = desc;
    myIndex = (myIndex + 1) % mySize;
  }

  public synchronized String getDescriptions() {
    return Joiner.on(',').skipNulls().join(myThrowableDescriptions);
  }
}
