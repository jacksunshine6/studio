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
package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.analytics.LastSeenExceptions;
import junit.framework.TestCase;

public class LastSeenExceptionsTest extends TestCase {
  private LastSeenExceptions myLastSeenExceptions;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLastSeenExceptions = new LastSeenExceptions(3);
  }

  public void testEmptyList() {
    assertEquals("", myLastSeenExceptions.getDescriptions());
  }

  public void testNullItems() {
    myLastSeenExceptions.add("a");
    myLastSeenExceptions.add("b");
    assertEquals("a,b", myLastSeenExceptions.getDescriptions());
  }

  public void testOverrun() {
    myLastSeenExceptions.add("a"); // 0
    myLastSeenExceptions.add("b"); // 1
    myLastSeenExceptions.add("c"); // 2
    myLastSeenExceptions.add("d"); // 0
    myLastSeenExceptions.add("e"); // 1

    assertEquals("d,e,c", myLastSeenExceptions.getDescriptions());
  }
}
