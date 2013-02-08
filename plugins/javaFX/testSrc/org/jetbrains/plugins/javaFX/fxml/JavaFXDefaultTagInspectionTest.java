/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxDefaultTagInspection;

import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFXDefaultTagInspectionTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JavaFxDefaultTagInspection()};
  }


  public void testChildren() throws Exception {
    doTest("children");
  }

  public void testEmptyChildren() throws Exception {
    doTest("children");
  }

  public void testStylesheets() throws Exception {
    checkNotAvailable("stylesheets");
  }

  private void doTest(String tagName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    final List<HighlightInfo> infos = doHighlighting();
    findAndInvokeIntentionAction(infos, "Unwrap '" + tagName + "'", getEditor(), getFile());
    checkResultByFile(getTestName(true) + "_after.fxml");
  }
  
  private void checkNotAvailable(String tagName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    final List<HighlightInfo> infos = doHighlighting();
    assertNull(findIntentionAction(infos, "Unwrap '" + tagName + "'", getEditor(), getFile()));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/defaultTag/";
  }
}
