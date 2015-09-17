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

import com.intellij.openapi.util.io.FileUtil;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

/**
 * Tests sharding logic in {@link UiTestTask}.
 */
public class UiTestTaskTest {

  private UiTestTask task;

  @Before
  public void setUp() throws Exception {
    task = new UiTestTask();
  }

  @Test
  public void testBatchComputation() throws Exception {
    File testRoot = FileUtil.createTempDirectory("test", "Root");
    createDirectoryStructure(testRoot);
    task.setProject(new Project());
    task.setTestRoot(testRoot.getAbsolutePath());
    task.setPackagePrefix("com");

    List<FileSet> batches = task.getTestBatches();
    assertEquals(2, batches.size());
    String spec1 = task.getTestSpec(batches.get(0));
    String spec2 = task.getTestSpec(batches.get(1));
    if (! ("com.bb.B1Test,com.bb.subdir.B2Test,".equals(spec1) && "com.aa.ATest,".equals(spec2)) ||
        ("com.bb.B1Test,bb.subdir.B2Test,".equals(spec2) && "com.aa.ATest,".equals(spec1))) {
      fail("Expected \"com.aa.ATest,\" and \"com.bb.B1Test,com.bb.subdir.B2Test,\", not \"" + spec1 + "\" and \"" + spec2 +"\"");
    }
  }

  private void createDirectoryStructure(File testRoot) throws Exception {
    // TODO: Can this be an in-memory filesystem?
    FileUtil.writeToFile(new File(testRoot, "com/aa/ATest.java"),
                         "package com.aa; public class ATest { }\n");
    FileUtil.writeToFile(new File(testRoot, "com/bb/B1Test.java"),
                         "package com.bb; public class B1Test { }\n");
    FileUtil.writeToFile(new File(testRoot, "com/bb/subdir/B2Test.java"),
                         "package com.bb.subdir; public class B2Test { }\n");
  }
}
