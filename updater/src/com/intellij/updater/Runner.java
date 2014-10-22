package com.intellij.updater;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

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
  public static Logger logger = null;

  /**
   * Treats zip files as regular binary files. When false, zip/jar files are unzipped and diffed file by file.
   * When true, the entire zip file is diffed as a single file. Set to true if preserving the timestamps of
   * the files inside the zip is important. This variable can change via a command line option.
   */
  public static boolean ZIP_AS_BINARY = false;

  private static final String PATCH_FILE_NAME = "patch-file.zip";

  public static void main(String[] args) throws Exception {

    String jarFile = getArgument(args, "jar");
    jarFile = jarFile == null ? resolveJarFile() : jarFile;

    if (args.length >= 6 && "create".equals(args[0])) {
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      initLogger();

      ZIP_AS_BINARY = Arrays.asList(args).contains("--zip_as_binary");

      List<String> ignoredFiles = extractFiles(args, "ignored");
      List<String> criticalFiles = extractFiles(args, "critical");
      List<String> optionalFiles = extractFiles(args, "optional");
      create(oldVersionDesc, newVersionDesc, oldFolder, newFolder, patchFile, jarFile, ignoredFiles, criticalFiles, optionalFiles);
    }
    else if (args.length >= 2 && "install".equals(args[0])) {
      String destFolder = args[1];
      initLogger();
      logger.info("destFolder: " + destFolder);

      install(jarFile, destFolder);
    }
    else {
      printUsage();
    }
  }

  // checks that log directory 1)exists 2)has write perm. and 3)has 1MB+ free space
  private static boolean isValidLogDir(String logFolder) {
    File fileLogDir = new File(logFolder);
    return fileLogDir.isDirectory() && fileLogDir.canWrite() && fileLogDir.getUsableSpace() >= 1000000;
  }

  private static String getLogDir() {
    String logFolder = System.getProperty("idea.updater.log");
    if (logFolder == null || !isValidLogDir(logFolder)) {
      logFolder = System.getProperty("java.io.tmpdir");
      if (!isValidLogDir(logFolder)) {
        logFolder = System.getProperty("user.home");
      }
    }
    return logFolder;
  }

  public static void initLogger() {
    if (logger == null) {
      String logFolder = getLogDir();
      FileAppender update = new FileAppender();

      update.setFile(new File(logFolder, "idea_updater.log").getAbsolutePath());
      update.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      update.setThreshold(Level.ALL);
      update.setAppend(true);
      update.activateOptions();

      FileAppender updateError = new FileAppender();
      updateError.setFile(new File(logFolder, "idea_updater_error.log").getAbsolutePath());
      updateError.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
      updateError.setThreshold(Level.ERROR);
      updateError.setAppend(false);
      updateError.activateOptions();

      logger = Logger.getLogger("com.intellij.updater");
      logger.addAppender(updateError);
      logger.addAppender(update);
      logger.setLevel(Level.ALL);

      logger.info("--- Updater started ---");
    }
  }

  public static void printStackTrace(Throwable e){
    logger.error(e.getMessage(), e);
  }

  public static String getArgument(String[] args, String name) {
    String flag = "--" + name + "=";
    for (String param : args) {
      if (param.startsWith(flag)) {
        return param.substring(flag.length());
      }
    }
    return null;
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

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void printUsage() {
    System.err.println("Usage:\n" +
                       "create <old_version_description> <new_version_description> <old_version_folder> <new_version_folder>" +
                       " <patch_file_name> [ignored=file1;file2;...] [critical=file1;file2;...] [optional=file1;file2;...]\n" +
                       "install <destination_folder>\n");
  }

  private static void create(String oldBuildDesc,
                             String newBuildDesc,
                             String oldFolder,
                             String newFolder,
                             String patchFile,
                             String jarFile,
                             List<String> ignoredFiles,
                             List<String> criticalFiles,
                             List<String> optionalFiles) throws IOException, OperationCancelledException {
    UpdaterUI ui = new ConsoleUpdaterUI();
    try {
      File tempPatchFile = Utils.createTempFile();
      PatchFileCreator.create(oldBuildDesc,
                              newBuildDesc,
                              new File(oldFolder),
                              new File(newFolder),
                              tempPatchFile,
                              ignoredFiles,
                              criticalFiles,
                              optionalFiles,
                              ui);

      logger.info("Packing jar file: " + patchFile );
      ui.startProcess("Packing jar file '" + patchFile + "'...");

      FileOutputStream fileOut = new FileOutputStream(patchFile);
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(new File(jarFile)));
        try {
          ZipEntry e;
          while ((e = in.getNextEntry()) != null) {
            out.zipEntry(e, in);
          }
        }
        finally {
          in.close();
        }

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
    logger.info("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static void install(final String jarFile, final String destFolder) throws Exception {
    // todo[r.sh] to delete in IDEA 14 (after a full circle of platform updates)
    if (System.getProperty("swing.defaultlaf") == null) {
      SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          }
          catch (Exception ignore) {
            printStackTrace(ignore);
          }
        }
      });
    }

    new SwingUpdaterUI(new SwingUpdaterUI.InstallOperation() {
                         public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                           logger.info("installing patch to the " + destFolder);
                           return doInstall(jarFile, ui, destFolder);
                         }
                       });
  }

  private static boolean doInstall(String jarFile, UpdaterUI ui, String destFolder) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile zipFile = new ZipFile(jarFile);

        logger.info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(zipFile, PATCH_FILE_NAME);
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
          zipFile.close();
        }

        ui.checkCancelled();

        File destDir = new File(destFolder);
        PatchFileCreator.PreparationResult result = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);
        Map<String, ValidationResult.Option> options = ui.askUser(result.validationResults);
        return PatchFileCreator.apply(result, options, ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }

    return false;
  }

  private static String resolveJarFile() throws IOException {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve jar file path");
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a 'jar' file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl)).getAbsolutePath();
    }
    catch (URISyntaxException e) {
      printStackTrace(e);
      throw new IOException(e.getMessage());
    }
  }
}
