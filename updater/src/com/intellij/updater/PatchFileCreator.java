package com.intellij.updater;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class PatchFileCreator {
  private static final String PATCH_INFO_FILE_NAME = ".patch-info";
  private static final String PATCH_PROPERTIES_ENTRY = "patch.properties";

  private static final String OLD_BUILD_DESCRIPTION = "old.build.description";
  private static final String NEW_BUILD_DESCRIPTION = "new.build.description";

  public static void create(String oldBuildDesc, String newBuildDesc, File olderDir,
                            File newerDir,
                            File patchFile,
                            List<String> ignoredFiles,
                            List<String> criticalFiles,
                            List<String> optionalFiles,
                            UpdaterUI ui) throws IOException, OperationCancelledException {

    Patch patchInfo = new Patch(olderDir, newerDir, ignoredFiles, criticalFiles, optionalFiles, ui);
    Runner.logger.info("Creating the patch file '" + patchFile + "'...");
    ui.startProcess("Creating the patch file '" + patchFile + "'...");
    ui.checkCancelled();

    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(patchFile));
    try {
      out.setLevel(9);

      out.putNextEntry(new ZipEntry(PATCH_INFO_FILE_NAME));
      patchInfo.write(out);
      out.closeEntry();

      out.putNextEntry(new ZipEntry(PATCH_PROPERTIES_ENTRY));
      Properties props = new Properties();
      props.setProperty(OLD_BUILD_DESCRIPTION, oldBuildDesc);
      props.setProperty(NEW_BUILD_DESCRIPTION, newBuildDesc);
      props.store(out, "");
      out.closeEntry();

      List<PatchAction> actions = patchInfo.getActions();
      for (PatchAction each : actions) {

        Runner.logger.info("Packing " + each.getPath());
        ui.setStatus("Packing " + each.getPath());
        ui.checkCancelled();
        each.buildPatchFile(olderDir, newerDir, out);
      }
    }
    finally {
      out.close();
    }
  }

  public static PreparationResult prepareAndValidate(File patchFile,
                                                     File toDir,
                                                     UpdaterUI ui) throws IOException, OperationCancelledException {
    Patch patch;

    ZipFile zipFile = new ZipFile(patchFile);
    try {

      InputStream in = Utils.getEntryInputStream(zipFile, PATCH_PROPERTIES_ENTRY);
      try {
        Properties props = new Properties();
        props.load(in);
        ui.setDescription(props.getProperty(OLD_BUILD_DESCRIPTION), props.getProperty(NEW_BUILD_DESCRIPTION));
      }
      finally {
        in.close();
      }

      in = Utils.getEntryInputStream(zipFile, PATCH_INFO_FILE_NAME);
      try {
        patch = new Patch(in);
      }
      finally {
        in.close();
      }
    }
    finally {
      zipFile.close();
    }

    List<ValidationResult> validationResults = patch.validate(toDir, ui);
    return new PreparationResult(patch, patchFile, toDir, validationResults);
  }

  public static boolean apply(PreparationResult preparationResult,
                              Map<String, ValidationResult.Option> options,
                              UpdaterUI ui) throws IOException, OperationCancelledException {
    return apply(preparationResult, options, Utils.createTempDir(), ui).applied;
  }

  public static Patch.ApplicationResult apply(PreparationResult preparationResult,
                                              Map<String, ValidationResult.Option> options,
                                              File backupDir,
                                              UpdaterUI ui) throws IOException, OperationCancelledException {
    ZipFile zipFile = new ZipFile(preparationResult.patchFile);
    try {
      return preparationResult.patch.apply(zipFile, preparationResult.toDir, backupDir, options, ui);
    }
    finally {
      zipFile.close();
    }
  }

  public static void revert(PreparationResult preparationResult,
                            List<PatchAction> actionsToRevert,
                            File backupDir,
                            UpdaterUI ui) throws IOException, OperationCancelledException {
    ZipFile zipFile = new ZipFile(preparationResult.patchFile);
    try {
      preparationResult.patch.revert(actionsToRevert, backupDir, preparationResult.toDir, ui);
    }
    finally {
      zipFile.close();
    }
  }

  public static class PreparationResult {
    public Patch patch;
    public File patchFile;
    public File toDir;
    public List<ValidationResult> validationResults;

    public PreparationResult(Patch patch, File patchFile, File toDir, List<ValidationResult> validationResults) {
      this.patch = patch;
      this.patchFile = patchFile;
      this.toDir = toDir;
      this.validationResults = validationResults;
    }
  }
}
