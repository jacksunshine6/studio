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
package com.intellij.ide;

import java.io.File;
import java.lang.reflect.Method;

public class BootstrapUITestRunner {

  private static final String JUNIT_CORE_CLASS = "org.junit.runner.JUnitCore";
  private static final String REQUEST_CLASS = "org.junit.runner.Request";

  private static final String UITESTS_ROOT = "plugins/android/ui-tests-dir";
  private static final String ANDROID_ROOT = "plugins/android/lib";

  private static final String SUITE_CLASS = "com.android.tools.idea.tests.gui.GuiTestSuite";


  private static String getTestClasspath() {
    StringBuilder classpath = new StringBuilder();
    // We have to load UI tests together with the android module classpath.
    classpath.append(new File(UITESTS_ROOT).getAbsolutePath());

    for(File entry : new File(ANDROID_ROOT).listFiles()) {
      if (entry.isFile() && entry.getName().endsWith(".jar")) {
        classpath.append(File.pathSeparator);
        classpath.append(entry.getAbsolutePath());
      }
    }
    return classpath.toString();
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("idea.additional.classpath", getTestClasspath());
    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);

    Class<?> testClass = Class.forName(SUITE_CLASS, true, newClassLoader);

    Class<?> jUnitCore = Class.forName(JUNIT_CORE_CLASS, true, newClassLoader);
    Object c = jUnitCore.newInstance();
    Class<?> request = Class.forName(REQUEST_CLASS, true, newClassLoader);
    Method runMethod = jUnitCore.getMethod("run", request);

    Method aClassMethod = request.getMethod("aClass", Class.class);
    runMethod.invoke(c, aClassMethod.invoke(request, testClass));
  }
}
