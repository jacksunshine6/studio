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
package com.android.antuitest.tasks;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.types.*;
import org.apache.tools.ant.types.Commandline.Argument;
import org.apache.tools.ant.types.selectors.FilenameSelector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Ant task for running UI tests.
 *
 * <p>The main advantage over a classic JUnitTask is the ability to shard tests to run in separate JVMs. We have more control over how to
 * split test batches (based on package name, annotations present, etc.) and over how to invoke them (because of command line restrictions
 * on Windows, our tests need special bootstrapping).</p>
 */
public class UiTestTask extends Task {

  private String classpathFile;
  private String testRoot;
  private String packagePrefix;
  private Path classpath;
  private final List<Argument> jvmArgs = new ArrayList<Argument>();

  public UiTestTask() throws Exception {
  }

  /**
   * Sets file containing actual test classpath.
   */
  public void setClasspathFile(String classpathFile) {
    this.classpathFile = classpathFile;
  }

  /**
   * Allows nested classpath elements, similar to JUnitTask.
   *
   * <p>Use this for the bootstrapping classpath, as this is added to the command line and can be huge!</p>
   */
  public Path createClasspath() {
    if (classpath == null) {
      classpath = new Path(getProject()).createPath();
    }
    return classpath;
  }

  /**
   * Allows nested jvmarg elements, similar to JUnitTask.
   */
  public Argument createJvmarg() {
    Argument jvmArg = new Argument();
    jvmArgs.add(jvmArg);
    return jvmArg;
  }

  public void setTestRoot(String testRoot) {
    this.testRoot = testRoot;
  }

  public void setPackagePrefix(String packagePrefix) {
    this.packagePrefix = packagePrefix;
  }

  @Override
  public void execute() throws BuildException {
    try {
      for (FileSet testSpec : getTestBatches()) {
        JUnitTask task = new JUnitTask();
        task.init();
        task.setProject(getProject());

        task.setFork(true);
        task.setForkMode(new JUnitTask.ForkMode("once"));

        task.setLogFailedTests(true);
        task.setShowOutput(true);
        task.setPrintsummary((JUnitTask.SummaryAttribute) EnumeratedAttribute.getInstance(JUnitTask.SummaryAttribute.class, "true"));

        task.createJvmarg().setValue("-Dclasspath.file=" + classpathFile);
        task.createJvmarg().setValue("-Dbootstrap.testcase=" + getTestSpec(testSpec));

        Path testClasspath = task.createClasspath();
        testClasspath.add(classpath);

        for (Argument jvmArg : jvmArgs) {
          task.createJvmarg().setValue(jvmArg.getParts()[0]);
        }

        FormatterElement plainFormatter = new FormatterElement();
        plainFormatter.setType(
          (FormatterElement.TypeAttribute) EnumeratedAttribute.getInstance(FormatterElement.TypeAttribute.class, "plain"));
        task.addFormatter(plainFormatter);

        FormatterElement xmlFormatter = new FormatterElement();
        xmlFormatter.setType(
          (FormatterElement.TypeAttribute) EnumeratedAttribute.getInstance(FormatterElement.TypeAttribute.class, "xml"));
        task.addFormatter(xmlFormatter);

        JUnitTest jUnitTest = new JUnitTest("com.intellij.tests.BootstrapUITests");
        // Make sure we set a different outfile for each invocation, to avoid test reports clobbering each other. Previously, all
        // invocations would write to TEST-com.intellij.tests.BootstrapUITests.txt.
        jUnitTest.setOutfile("TEST-" + testSpec.getDescription());
        task.addTest(jUnitTest);

        log("Executing UI tests in " + testSpec.getDescription());
        task.execute();
      }
    } catch (Exception ex) {
      log(ex.getMessage(), ex, Project.MSG_ERR);
      throw new BuildException(ex);
    }
  }

  /*
   * TODO: Replace this naive file-based sharding with the proper annotation-based approach (use a classloader and get the runtime
   * classpath from classpathFile, load GuiTestSuite and scan through its test groups).
   */
  public List<FileSet> getTestBatches() {
    List<FileSet> result = new ArrayList<FileSet>();
    if (!packagePrefix.endsWith("/")) {
      packagePrefix += "/";
    }

    File rootFile = new File(testRoot);
    File suiteDir = new File(rootFile, packagePrefix);

    for (File subdir : suiteDir.listFiles()) {
      if (subdir.isDirectory()) {
        FilenameSelector testSelector = new FilenameSelector();
        testSelector.setProject(getProject());
        testSelector.setName(packagePrefix + subdir.getName() + "/**/*Test.java");

        FileSet fileSet = new FileSet();
        fileSet.setProject(getProject());
        fileSet.setDescription(subdir.getName());
        fileSet.setDir(rootFile.getAbsoluteFile());
        fileSet.addFilename(testSelector);
        result.add(fileSet);
      }
    }
    return result;
  }

  /**
   * Returns a comma-separated list of test class names, to be passed via -Dbootstrap.testcase to BootstrapUITests.
   */
  public String getTestSpec(FileSet fileSet) {
    StringBuilder sb = new StringBuilder();
    for (Resource file : fileSet) {
      sb.append(file.getName().replace("/", ".").replace(".java", ""));
      sb.append(",");
    }
    return sb.toString();
  }
}
