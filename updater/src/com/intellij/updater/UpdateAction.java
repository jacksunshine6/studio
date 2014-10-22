package com.intellij.updater;

import java.io.*;

public class UpdateAction extends BaseUpdateAction {
  public UpdateAction(Patch patch, File olderDir, String path, String source, long checksum, boolean move) {
    super(patch, olderDir, path, source, checksum, move);
  }

  public UpdateAction(Patch patch, File olderDir, String path, long checksum) {
    this(patch, olderDir, path, path, checksum, false);
  }

  public UpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File toFile, MultiZipFile.OutputStream patchOutput) throws IOException {
    if (!myIsMove) {
      File source = getSource(myOlderDir);
      patchOutput.putNextEntry(myPath);
      writeExecutableFlag(patchOutput, toFile);
      writeDiff(source, toFile, patchOutput);
      patchOutput.closeEntry();
    }
  }

  @Override
  protected void doApply(MultiZipFile patchFile, File backupDir, File toFile) throws IOException {
    File source = getSource(backupDir);
    File updated;
    if (!myIsMove) {
      updated = Utils.createTempFile();
      InputStream in = Utils.findEntryInputStream(patchFile, myPath);
      boolean executable = readExecutableFlag(in);

      OutputStream out = new BufferedOutputStream(new FileOutputStream(updated));
      try {
        InputStream oldFileIn = new FileInputStream(source);
        try {
          applyDiff(in, oldFileIn, out);
        }
        finally {
          oldFileIn.close();
        }
      }
      finally {
        out.close();
      }
      Utils.setExecutable(updated, executable);
    } else {
      updated = source;
    }
    replaceUpdated(updated, toFile);
  }
}
