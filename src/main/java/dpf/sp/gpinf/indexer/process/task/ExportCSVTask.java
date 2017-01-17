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

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Properties;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Worker;

/**
 * Responsável por gerar arquivo CSV com as propriedades dos itens processados.
 */
public class ExportCSVTask extends AbstractTask {

  private static int MAX_MEM_SIZE = 1000000;
  private static String CSV_NAME = "Lista de Arquivos.csv";

  public static boolean exportFileProps = false;
  public static volatile boolean headerWritten = false;

  private StringBuilder list = new StringBuilder();

  public ExportCSVTask(Worker worker) throws NoSuchAlgorithmException, IOException {
    super(worker);
    this.output = new File(output.getParentFile(), CSV_NAME);
    if (output.exists() && !IndexFiles.getInstance().appendIndex) {
      Files.delete(output.toPath());
    }
  }

  /**
   * Indica que itens ignorados, como duplicados ou kff ignorable, devem ser listados no arquivo
   * CSV.
   *
   * @return true
   */
  @Override
  protected boolean processIgnoredItem() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return exportFileProps;
  }
  
  @Override
  protected void process(EvidenceFile evidence) throws IOException {

    if (!exportFileProps || (caseData.isIpedReport() && !evidence.isToAddToCase())) {
      return;
    }

    String value = evidence.getName();
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getFileToIndex();
    if (!value.isEmpty() && caseData.containsReport() && evidence.isToAddToCase() && !evidence.isToIgnore()) {
      value = "=HIPERLINK(\"\"" + value + "\"\";\"\"Abrir\"\")";
    } else {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    Long length = evidence.getLength();
    if (length == null) {
      value = "";
    } else {
      value = length.toString();
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getExt();
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getLabels();
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getCategories().replace("" + CategoryTokenizer.SEPARATOR, " | ");
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getHash();
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    value = Boolean.toString(evidence.isDeleted());
    list.append("\"" + escape(value) + "\";");

    value = Boolean.toString(evidence.isCarved());
    list.append("\"" + escape(value) + "\";");

    Date date = evidence.getAccessDate();
    if (date == null) {
      value = "";
    } else {
      value = date.toString();
    }
    list.append("\"" + escape(value) + "\";");

    date = evidence.getModDate();
    if (date == null) {
      value = "";
    } else {
      value = date.toString();
    }
    list.append("\"" + escape(value) + "\";");

    date = evidence.getCreationDate();
    if (date == null) {
      value = "";
    } else {
      value = date.toString();
    }
    list.append("\"" + escape(value) + "\";");

    value = evidence.getPath();
    if (value == null) {
      value = "";
    }
    list.append("\"" + escape(value) + "\";");

    list.append("\r\n");

    if (list.length() > MAX_MEM_SIZE) {
      flush();
    }

  }
  
  private String escape(String value){
	  StringBuilder str = new StringBuilder(); 
	  for(char c : value.trim().toCharArray())
		  if(c >= '\u0020' && !(c >= '\u007F' && c <= '\u009F'))
			  str.append(c);
	  
	  return str.toString().replace("\"", "\"\"");
  }

  public void flush() throws IOException {
    flush(list, output);
    list = new StringBuilder();
  }

  private static synchronized void flush(StringBuilder list, File output) throws IOException {
	FileOutputStream fos =  new FileOutputStream(output, true);
    OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
    if (!headerWritten) {
      byte[] utf8bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
      fos.write(utf8bom);
      writer.write("\"Nome\";\"Atalho\";\"Tamanho\";\"Ext\";\"Marcador\";\"Categoria\";\"Hash\";\"Deletado\";\"Recuperado\";\"Acesso\";\"Modificação\";\"Criação\";\"Caminho\";\r\n");
      headerWritten = true;
    }
    writer.write(list.toString());
    writer.close();
  }

  public void finish() throws IOException {
    if (exportFileProps) {
      flush();
    }
  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {

    String value = confProps.getProperty("exportFileProps");
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      exportFileProps = Boolean.valueOf(value);
    }

    headerWritten = false;

  }

}
