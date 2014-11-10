package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;

public class ForCanBeForeachInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    ForCanBeForeachInspection inspection = new ForCanBeForeachInspection();

    // Android Studio: Changed default to false, but this test is for indexed-checks=true.
    inspection.REPORT_INDEXED_LOOP = true;

    doTest("com/siyeh/igtest/migration/foreach", new LocalInspectionToolWrapper(inspection), "java 1.5");
  }
}