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
package com.intellij.tests;

import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class BootstrapUITests {
  static {
    ExternalClasspathClassLoader.install();
  }

  public static TestSuite suite() throws Exception {
    TestSuite suite = new TestSuite();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Class<?> testClass = Class.forName(System.getProperty("bootstrap.testcase"), true, cl);
    // Simplifying assumption: all our tests are properly annotated with @RunWith
    suite.addTest(new JUnit4TestAdapter(testClass));
    return suite;
  }
}
