package br.gov.pf.labld.graph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.neo4j.tooling.ImportTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphImportRunner {

  private static Logger LOGGER = LoggerFactory.getLogger(GraphImportRunner.class);

  public static final String ARGS_FILE_NAME = "import-tool-args";

  private File root;

  public GraphImportRunner(File root) {
    super();
    this.root = root;
  }

  public void run(File databaseDir, String dbName, boolean highIO) throws IOException {

    List<String> args = new ArrayList<>();

    args.add(getJreExecutable().getAbsolutePath());
    args.add("-cp");
    args.add(ImportTool.class.getProtectionDomain().getCodeSource().getLocation().getPath());

    args.add(ImportTool.class.getName());

    File argsFile = writeArgsFile(databaseDir, dbName, highIO);
    args.add("--f");
    args.add(argsFile.getAbsolutePath());

    ProcessBuilder processBuilder = new ProcessBuilder(args);
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();
    try {
      int result = process.waitFor();
      String output = IOUtils.toString(process.getInputStream(), Charset.forName("utf-8"));
      LOGGER.info(output);
      if (result != 0) {
        throw new RuntimeException("Could not import graph database.");
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      process.destroy();
    }
  }

  public File writeArgsFile(File databaseDir, String dbName, boolean highIO) throws IOException {
    File file = new File(root, ARGS_FILE_NAME + ".txt");
    try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {

      writer.write("--into ");
      writer.write(databaseDir.getAbsolutePath());
      writer.write("\r\n");
      writer.write("--input-encoding utf-8\r\n");
      writer.write("--bad-tolerance 0\r\n");
      writer.write("--database ");
      writer.write(dbName);
      writer.write("\r\n");
      writer.write("--high-io ");
      writer.write(Boolean.toString(highIO));
      writer.write("\r\n");
      writer.write("--ignore-empty-strings true\r\n");
      writer.write("--skip-duplicate-nodes true\r\n");

      File[] argsFiles = root.listFiles(new ArgsFileFilter());
      for (File argFile : argsFiles) {
        writer.write(IOUtils.toString(argFile.toURI(), Charset.forName("utf-8")));
      }
    }
    return file;
  }

  private static class ArgsFileFilter implements FileFilter {

    @Override
    public boolean accept(File pathname) {
      return pathname.getName().startsWith(ARGS_FILE_NAME);
    }

  }

  private boolean isWindows() {
    String os = System.getProperty("os.name");
    if (os == null) {
      throw new IllegalStateException("os.name");
    }
    os = os.toLowerCase();
    return os.startsWith("windows");
  }

  private File getJreExecutable() throws FileNotFoundException {
    String jreDirectory = System.getProperty("java.home");
    if (jreDirectory == null) {
      throw new IllegalStateException("java.home");
    }
    File exe;
    if (isWindows()) {
      exe = new File(jreDirectory, "bin/java.exe");
    } else {
      exe = new File(jreDirectory, "bin/java");
    }
    if (!exe.isFile()) {
      throw new FileNotFoundException(exe.toString());
    }
    return exe;
  }

}
