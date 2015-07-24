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
package com.intellij.util;

/**
 * A set of configuration flags that are wanted at compile time.
 */
public class PlatformConfig {

  /**
   * Whether on EAP builds, the update channel is forced to be EAP.
   */
  public static boolean FORCE_EAP_UPDATE_CHANNEL = true;

  /**
   * Whether the updater will allow patch updates to cross major version boundaries.
   */
  public static boolean ALLOW_MAJOR_VERSION_UPDATE = false;

  private PlatformConfig() {} // Do not instantiate.
}
