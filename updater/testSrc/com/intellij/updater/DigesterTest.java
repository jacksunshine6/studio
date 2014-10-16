package com.intellij.updater;

import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    assertEquals(CHECKSUMS.README_TXT, Digester.digestRegularFile(new File(getDataDir(), "Readme.txt")));
    assertEquals(CHECKSUMS.FOCUSKILLER_DLL, Digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BINARY, Digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar")));
  }
}