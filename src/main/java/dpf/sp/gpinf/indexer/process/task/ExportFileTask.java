/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.parsers.util.ExportFolder;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

/**
 * Responsável por extrair subitens de containers. Também exporta itens ativos em casos de extração
 * automática de dados ou em casos de extração de itens selecionados após análise.
 */
public class ExportFileTask extends AbstractTask {

  private static Logger LOGGER = LoggerFactory.getLogger(ExportFileTask.class);
  public static final String EXTRACT_CONFIG = "CategoriesToExport.txt"; //$NON-NLS-1$
  public static final String EXTRACT_DIR = Messages.getString("ExportFileTask.ExportFolder"); //$NON-NLS-1$
  private static final String SUBITEM_DIR = "subitens"; //$NON-NLS-1$
  
  private static final int MAX_SUBITEM_COMPRESSION = 100;
  private static final int ZIPBOMB_MIN_SIZE = 10 * 1024 * 1024;

  private static HashSet<String> categoriesToExtract = new HashSet<String>();
  public static int subDirCounter = 0, itensExtracted = 0;
  private static File subDir;

  private static boolean computeHash = false;
  private File extractDir;
  private HashMap<HashValue, HashValue> hashMap;
  private List<String> noContentLabels;

  public ExportFileTask() {
    ExportFolder.setExportPath(EXTRACT_DIR);
  }

  public static synchronized void incItensExtracted() {
    itensExtracted++;
  }

  public static int getItensExtracted() {
    return itensExtracted;
  }

  private void setExtractDir() {
    if (output != null) {
      if (caseData.containsReport()) {
        this.extractDir = new File(output.getParentFile(), EXTRACT_DIR);
      } else {
        this.extractDir = new File(output, SUBITEM_DIR);
      }
    }
  }

  public static void load(File file) throws FileNotFoundException, IOException {

    String content = Util.readUTF8Content(file);
    for (String line : content.split("\n")) { //$NON-NLS-1$
      if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
        continue;
      }
      categoriesToExtract.add(line.trim());
    }
  }

  private static synchronized File getSubDir(File extractDir) {
    if (subDirCounter % 1000 == 0) {
      subDir = new File(extractDir, Integer.toString(subDirCounter / 1000));
      if (!subDir.exists()) {
        subDir.mkdirs();
      }
    }
    subDirCounter++;
    return subDir;
  }

  public static boolean hasCategoryToExtract() {
    return categoriesToExtract.size() > 0;
  }

  public static boolean isToBeExtracted(EvidenceFile evidence) {

    boolean result = false;
    for (String category : evidence.getCategorySet()) {
      if (categoriesToExtract.contains(category)) {
        result = true;
        break;
      }
    }

    return result;
  }

  public void process(EvidenceFile evidence) {

    // Exporta arquivo no caso de extração automatica ou no caso de relatório do iped
    if ((caseData.isIpedReport() && evidence.isToAddToCase())
        || (!evidence.isSubItem() && (isToBeExtracted(evidence) || evidence.isToExtract()))) {

      evidence.setToExtract(true);
      if (doNotExport(evidence)) {
        evidence.setSleuthId(null);
        evidence.setExportedFile(null);
      } else {
        extract(evidence);
      }

      incItensExtracted();
      copyViewFile(evidence);
    }

    //Renomeia subitem caso deva ser exportado
    if (!caseData.isIpedReport() && evidence.isSubItem()
            && (evidence.isToExtract() || isToBeExtracted(evidence) || 
                    !(hasCategoryToExtract() || RegexTask.isExtractByKeywordsOn()) )) {

      evidence.setToExtract(true);
      if (!doNotExport(evidence)) {
        renameToHash(evidence);
      } else {
        evidence.setExportedFile(null);
        evidence.setDeleteFile(true);
      }
      incItensExtracted();
    }

    if ((hasCategoryToExtract() || RegexTask.isExtractByKeywordsOn()) && !evidence.isToExtract()) {
      evidence.setAddToCase(false);
    }

  }
  
  private boolean doNotExport(EvidenceFile evidence) {
    if (noContentLabels == null) {
      CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
      noContentLabels = args.getNocontent();
      if (noContentLabels == null) {
        noContentLabels = Collections.emptyList();
      }
    }
    if (noContentLabels.isEmpty()) return false;
    Collection<String> evidenceLabels = evidence.getLabels().isEmpty() ? evidence.getCategorySet() : evidence.getLabels();
    for (String label : evidenceLabels) {
      boolean isNoContent = false;
      for (String noContentLabel : noContentLabels) {
        if (label.equalsIgnoreCase(noContentLabel)) {
          isNoContent = true;
          break;
        }
      }
      if (!isNoContent) return false;
    }
    return true;
  }

  public void extract(EvidenceFile evidence) {
    InputStream is = null;
    try {
      is = evidence.getBufferedStream();
      extractFile(is, evidence, null);
      evidence.setFileOffset(-1);

    } catch (IOException e) {
      LOGGER.warn("{} Error exporting {} \t{}", Thread.currentThread().getName(), evidence.getPath(), e.toString()); //$NON-NLS-1$

    } finally {
      IOUtil.closeQuietly(is);
    }
  }

  private void copyViewFile(EvidenceFile evidence) {
    File viewFile = evidence.getViewFile();
    if (viewFile != null) {
      String viewName = viewFile.getName();
      File destFile = new File(output, "view/" + viewName.charAt(0) + "/" + viewName.charAt(1) + "/" + viewName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      destFile.getParentFile().mkdirs();
      try {
        IOUtil.copiaArquivo(viewFile, destFile);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private File getHashFile(String hash, String ext) {
    String path = hash.charAt(0) + "/" + hash.charAt(1) + "/" + Util.getValidFilename(hash + ext); //$NON-NLS-1$ //$NON-NLS-2$
    if (extractDir == null) {
      setExtractDir();
    }
    File result = new File(extractDir, path);
    File parent = result.getParentFile();
    if (!parent.exists()) {
      try {
        Files.createDirectories(parent.toPath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return result;
  }

  public void renameToHash(EvidenceFile evidence) {

    String hash = evidence.getHash();
    if (hash != null && !hash.isEmpty()) {
      File file = evidence.getFile();
      String ext = evidence.getType().getLongDescr();
      if (!ext.isEmpty()) {
        ext = "." + ext; //$NON-NLS-1$
      }
      ext = Util.removeNonLatin1Chars(ext);
      
      File hashFile = getHashFile(hash, ext);

      HashValue hashVal = new HashValue(hash);
      HashValue hashLock;
      synchronized (hashMap) {
        hashLock = hashMap.get(hashVal);
      }

      synchronized (hashLock) {
        if (!hashFile.exists()) {
          try {
            Files.move(file.toPath(), hashFile.toPath());
            changeTargetFile(evidence, hashFile);

          } catch (IOException e) {
            // falha ao renomear pode ter sido causada por outra thread
            // criando arquivo com mesmo hash entre as 2 chamadas acima
            if (hashFile.exists()) {
              changeTargetFile(evidence, hashFile);
              if (!file.delete()) {
                LOGGER.warn("{} Error deleting {}", Thread.currentThread().getName(), file.getAbsolutePath()); //$NON-NLS-1$
              }
            } else {
              LOGGER.warn("{} Error renaming to hash: {}", Thread.currentThread().getName(), evidence.getFileToIndex()); //$NON-NLS-1$
              e.printStackTrace();
            }
          }

        } else {
          changeTargetFile(evidence, hashFile);
          if (!file.delete()) {
            LOGGER.warn("{} Error Deleting {}", Thread.currentThread().getName(), file.getAbsolutePath()); //$NON-NLS-1$
          }
        }
      }

    }

  }

  private void changeTargetFile(EvidenceFile evidence, File file) {
    String relativePath = Util.getRelativePath(output, file);
    evidence.setExportedFile(relativePath);
    evidence.setFile(file);
  }

  public void extractFile(InputStream inputStream, EvidenceFile evidence, Long parentSize) throws IOException {

    String hash;
    File outputFile = null;
    Object hashLock = new Object();

    String ext = ""; //$NON-NLS-1$
    if (evidence.getType() != null) {
      ext = evidence.getType().getLongDescr();
    }
    if (!ext.isEmpty()) {
      ext = "." + ext; //$NON-NLS-1$
    }
    
    ext = Util.removeNonLatin1Chars(ext);

    if (extractDir == null) {
      setExtractDir();
    }

    if (!computeHash) {
      outputFile = new File(getSubDir(extractDir), Util.getValidFilename(Integer.toString(evidence.getId()) + ext));
    } else if ((hash = evidence.getHash()) != null && !hash.isEmpty()) {
      outputFile = getHashFile(hash, ext);
      HashValue hashVal = new HashValue(hash);
      synchronized (hashMap) {
        hashLock = hashMap.get(hashVal);
      }

    } else {
      outputFile = new File(extractDir, Util.getValidFilename("0" + Integer.toString(evidence.getId()) + ext)); //$NON-NLS-1$
      if (!outputFile.getParentFile().exists()) {
        outputFile.getParentFile().mkdirs();
      }
    }

    synchronized (hashLock) {
      if (outputFile.createNewFile()) {
        BufferedOutputStream bos = null;
        try {
          bos = new BufferedOutputStream(new FileOutputStream(outputFile));
          BufferedInputStream bis = new BufferedInputStream(inputStream);
          byte[] buf = new byte[1024 * 1024];
  		  int total = 0,len;
  		  while ((len = bis.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()){
  			bos.write(buf, 0, len);
  			total += len;
  			if(parentSize != null && total >= ZIPBOMB_MIN_SIZE && 
  					total > parentSize * MAX_SUBITEM_COMPRESSION)
  				throw new IOException("Potential zip bomb while extracting subitem!"); //$NON-NLS-1$
  		  }
  		
        } catch (IOException e) {
          if(IOUtil.isDiskFull(e))
        	  LOGGER.error("Error exporting {}\t{}", evidence.getPath(), "No space left on output disk!"); //$NON-NLS-1$ //$NON-NLS-2$
          else
        	  LOGGER.warn("Error exporting {}\t{}", evidence.getPath(), e.toString()); //$NON-NLS-1$

        } finally {
          if (bos != null) {
            bos.close();
          }
        }
      }
    }

    changeTargetFile(evidence, outputFile);

    if (evidence.isSubItem()) {
      evidence.setLength(outputFile.length());
    }

  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {
    load(new File(confDir, EXTRACT_CONFIG));

    if (hasCategoryToExtract()) {
      caseData.setContainsReport(true);
    }

    String value = confProps.getProperty("hash"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      computeHash = true;
    }

    itensExtracted = 0;
    subDirCounter = 0;

    hashMap = (HashMap<HashValue, HashValue>) caseData.getCaseObject(DuplicateTask.HASH_MAP);

  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

}
