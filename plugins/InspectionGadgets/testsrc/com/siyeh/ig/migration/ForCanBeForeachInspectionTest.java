package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class ForCanBeForeachInspectionTest extends LightInspectionTestCase {

  public void testForCanBeForEach() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    ForCanBeForeachInspection inspection = new ForCanBeForeachInspection();

    // Android Studio: Changed default to false, but this test is for indexed-checks=true.
    inspection.REPORT_INDEXED_LOOP = true;

    return inspection;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/migration/foreach";
  }
}
