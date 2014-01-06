package com.intellij.updater;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Runner {
  private static final String PATCH_FILE_NAME = "patch-file.zip";
  private static final String PATCH_PROPERTIES_ENTRY = "patch.properties";
  private static final String OLD_BUILD_DESCRIPTION = "old.build.description";
  private static final String NEW_BUILD_DESCRIPTION = "new.build.description";

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      printUsage();
      return;
    }

    String command = args[0];

    if ("create".equals(command)) {
      if (args.length < 6) {
        printUsage();
        return;
      }
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      List<String> ignoredFiles = extractFiles(args, "ignored");
      List<String> criticalFiles = extractFiles(args, "critical");
      List<String> optionalFiles = extractFiles(args, "optional");
      create(oldVersionDesc, newVersionDesc, oldFolder, newFolder, patchFile, ignoredFiles, criticalFiles, optionalFiles);
    }
    else if ("install".equals(command)) {
      int n = 2;

      // Default install exit code is SwingUpdaterUI.RESULT_REQUIRES_RESTART (42) unless overridden to be 0.
      // This is used by testUI/build.gradle as gradle expects a javaexec to exit with code 0.
      boolean useExitCode0 = false;
      if (args.length == 3 && args[1].equals("--exit0")) {
        n++;
        useExitCode0 = true;
      }
      if (args.length < n) {
        printUsage();
        return;
      }

      String destFolder = args[n - 1];
      install(useExitCode0, destFolder);
    }
    else {
      printUsage();
      return;
    }
  }

  public static List<String> extractFiles(String[] args, String paramName) {
    List<String> result = new ArrayList<String>();
    for (String param : args) {
      if (param.startsWith(paramName + "=")) {
        param = param.substring((paramName + "=").length());
        for (StringTokenizer tokenizer = new StringTokenizer(param, ";"); tokenizer.hasMoreTokens();) {
          String each = tokenizer.nextToken();
          result.add(each);
        }
      }
    }
    return result;
  }

  private static void printUsage() {
    System.err.println("Usage:\n" +
                       "create <old_version_description> <new_version_description> <old_version_folder> <new_version_folder>" +
                       " <patch_file_name> [ignored=file1;file2;...] [critical=file1;file2;...] [optional=file1;file2;...]\n" +
                       "install [--exit0] <destination_folder>\n");
  }

  private static void create(String oldBuildDesc,
                             String newBuildDesc,
                             String oldFolder,
                             String newFolder,
                             String patchFile,
                             List<String> ignoredFiles,
                             List<String> criticalFiles,
                             List<String> optionalFiles) throws IOException, OperationCancelledException {
    File tempPatchFile = Utils.createTempFile();
    createImpl(oldBuildDesc,
               newBuildDesc,
               oldFolder,
               newFolder,
               patchFile,
               tempPatchFile,
               ignoredFiles,
               criticalFiles,
               optionalFiles,
               new ConsoleUpdaterUI(), resolveJarFile());
  }

  static void createImpl(String oldBuildDesc,
                         String newBuildDesc,
                         String oldFolder,
                         String newFolder,
                         String outPatchJar,
                         File   tempPatchFile,
                         List<String> ignoredFiles,
                         List<String> criticalFiles,
                         List<String> optionalFiles,
                         UpdaterUI ui,
                         File resolvedJar) throws IOException, OperationCancelledException {
    try {
      PatchFileCreator.create(new File(oldFolder),
                              new File(newFolder),
                              tempPatchFile,
                              ignoredFiles,
                              criticalFiles,
                              optionalFiles,
                              ui);

      ui.startProcess("Packing jar file '" + outPatchJar + "'...");

      FileOutputStream fileOut = new FileOutputStream(outPatchJar);
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(resolvedJar));
        try {
          ZipEntry e;
          while ((e = in.getNextEntry()) != null) {
            out.zipEntry(e, in);
          }
        }
        finally {
          in.close();
        }

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
          Properties props = new Properties();
          props.setProperty(OLD_BUILD_DESCRIPTION, oldBuildDesc);
          props.setProperty(NEW_BUILD_DESCRIPTION, newBuildDesc);
          props.store(byteOut, "");
        }
        finally {
          byteOut.close();
        }

        out.zipBytes(PATCH_PROPERTIES_ENTRY, byteOut);
        out.zipFile(PATCH_FILE_NAME, tempPatchFile);
        out.finish();
      }
      finally {
        fileOut.close();
      }
    }
    finally {
      cleanup(ui);
    }
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static void install(final boolean useExitCode0, final String destFolder) throws Exception {
    InputStream in = Runner.class.getResourceAsStream("/" + PATCH_PROPERTIES_ENTRY);
    Properties props = new Properties();
    try {
      props.load(in);
    }
    finally {
      in.close();
    }

    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception ignore) {
        }
      }
    });

    new SwingUpdaterUI(props.getProperty(OLD_BUILD_DESCRIPTION),
                  props.getProperty(NEW_BUILD_DESCRIPTION),
                  useExitCode0 ? 0 : SwingUpdaterUI.RESULT_REQUIRES_RESTART,
                  new SwingUpdaterUI.InstallOperation() {
                    @Override
                    public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                      return doInstall(ui, destFolder);
                    }
                  });
  }

  interface IJarResolver {
    File resolveJar() throws IOException;
  }

  private static boolean doInstall(UpdaterUI ui, String destFolder) throws OperationCancelledException {
    return doInstallImpl(ui, destFolder, new IJarResolver() {
      @Override
      public File resolveJar() throws IOException {
        return resolveJarFile();
      }
    });
  }

  static boolean doInstallImpl(UpdaterUI ui,
                               String destFolder,
                               IJarResolver jarResolver) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile jarFile = new ZipFile(jarResolver.resolveJar());

        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(jarFile, PATCH_FILE_NAME);
          OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile));
          try {
            Utils.copyStream(in, out);
          }
          finally {
            in.close();
            out.close();
          }
        }
        finally {
          jarFile.close();
        }

        ui.checkCancelled();

        File destDir = new File(destFolder);
        PatchFileCreator.PreparationResult result = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);
        Map<String, ValidationResult.Option> options = ui.askUser(result.validationResults);
        return PatchFileCreator.apply(result, options, ui);
      }
      catch (IOException e) {
        ui.showError(e);
      }
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
      }
    }

    return false;
  }

  private static File resolveJarFile() throws IOException {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve jar file path");
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a 'jar' file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl));
    }
    catch (URISyntaxException e) {
      throw new IOException(e.getMessage());
    }
  }
}
