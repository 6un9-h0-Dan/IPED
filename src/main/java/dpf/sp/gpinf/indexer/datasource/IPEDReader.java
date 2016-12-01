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
package dpf.sp.gpinf.indexer.datasource;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.GenericFileType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.desktop.ColumnsManager;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IPEDReader extends DataSourceReader {

  private static Object lock = new Object();

  //Referência estática para a JVM não finalizar o objeto que será usado futuramente
  //via referência interna ao JNI para acessar os itens do caso
  static SleuthkitCase sleuthCase;

  IPEDSource ipedCase;
  HashSet<Integer> selectedLabels;
  Marcadores state;
  File indexDir;
  String basePath;

  public IPEDReader(CaseData caseData, File output, boolean listOnly) {
    super(caseData, output, listOnly);
  }

  public boolean isSupported(File report) {
    String name = report.getName().toLowerCase();
    return name.endsWith(Marcadores.EXT);
  }

  public int read(File file) throws Exception {

    caseData.setContainsReport(true);
    caseData.setIpedReport(true);

    // Configuração para não expandir containers
    ParsingTask.expandContainers = false;
    CarveTask.enableCarving = false;

    Object obj = Util.readObject(file.getAbsolutePath());
    if(obj instanceof MultiMarcadores)
    	throw new IPEDException("Atualmente não é possível gerar relatórios a partir de múltiplos casos!"
    			+ "Gere o relatório para cada caso separadamente.");
    
    state = (Marcadores)obj;
    indexDir = state.getIndexDir().getCanonicalFile();
    basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
    String dbPath = basePath + File.separator + SleuthkitReader.DB_NAME;

    //TODO remover e usar sleuthCase interno ao ipedCase?
    synchronized (lock) {
      if (new File(dbPath).exists() && sleuthCase == null) {
        sleuthCase = SleuthkitCase.openCase(dbPath);
      }
    }

    Logger.getLogger("org.sleuthkit").setLevel(Level.SEVERE);
    
    ipedCase = new IPEDSource(new File(basePath));

    selectedLabels = new HashSet<Integer>();
    
    IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
	LuceneSearchResult result = state.filtrarSelecionados(pesquisa.luceneSearch(), ipedCase);

    insertIntoProcessQueue(result, false);

    //Inclui anexos de emails de PST
    insertPSTAttachs(result);

    //Inclui pais para visualização em árvore
    insertParentTreeNodes(result);

    if (!listOnly) {
      for (int labelId : state.getLabelMap().keySet().toArray(new Integer[0])) {
        if (!selectedLabels.contains(labelId)) {
          state.delLabel(labelId);
        }
      }

      state.resetAndSetIndexDir(new File(output, "index"));
      state.saveState(new File(output, Marcadores.STATEFILENAME));
    }

    ipedCase.close();

    return 0;

  }

  private void insertParentTreeNodes(LuceneSearchResult result) throws Exception {
    boolean[] isParentToAdd = new boolean[ipedCase.getLastId() + 1];
    for (int docID : result.getLuceneIds()) {
      String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
      if(!parentIds.trim().isEmpty())
	      for (String parentId : parentIds.trim().split(" ")) {
	        isParentToAdd[Integer.parseInt(parentId)] = true;
	      }
    }
    for (int docID : result.getLuceneIds()) {
      String id = ipedCase.getReader().document(docID).get(IndexItem.ID);
      isParentToAdd[Integer.parseInt(id)] = false;
    }
    int num = 0;
    BooleanQuery query = new BooleanQuery();
    for (int i = 0; i <= ipedCase.getLastId(); i++) {
      if (isParentToAdd[i]) {
        query.add(NumericRangeQuery.newIntRange(IndexItem.ID, i, i, true, true), Occur.SHOULD);
        num++;
      }
      if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
    	IPEDSearcher searchParents = new IPEDSearcher(ipedCase, query);
  		searchParents.setTreeQuery(true);
        result = searchParents.luceneSearch();
        insertIntoProcessQueue(result, true);
        query = new BooleanQuery();
        num = 0;
      }
    }
  }

  private void insertPSTAttachs(LuceneSearchResult result) throws Exception {
    CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
    if (!args.getCmdArgs().containsKey("--nopstattachs")) {
      boolean[] isSelectedPSTEmail = new boolean[ipedCase.getLastId() + 1];
      boolean hasPSTEmail = false;
      for (int docID : result.getLuceneIds()) {
        String mimetype = ipedCase.getReader().document(docID).get(IndexItem.CONTENTTYPE);
        if (OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype)) {
          hasPSTEmail = true;
          isSelectedPSTEmail[Integer.parseInt(ipedCase.getReader().document(docID).get(IndexItem.ID))] = true;
        }
      }
      if(!hasPSTEmail)
        return;
      
      //search attachs
      int num = 0;
      boolean[] isAttachToAdd = new boolean[ipedCase.getLastId() + 1];
      String queryStr = IndexItem.PARENTID + ":(";
      for (int i = 0; i <= ipedCase.getLastId(); i++) {
          if (isSelectedPSTEmail[i]) {
        	queryStr += (i + " ");
            num++;
          }
          if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
        	queryStr += ")";
            IPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, queryStr);
            SearchResult attachs = searchAttachs.search();
      	    for(int j = 0; j < attachs.getLength(); j++)
      	    	isAttachToAdd[attachs.getId(j)] = true;
      	    queryStr = IndexItem.PARENTID + ":(";
            num = 0;
          }
      }
      
      //remove duplicate attachs
      for (int docID : result.getLuceneIds()) {
        String id = ipedCase.getReader().document(docID).get(IndexItem.ID);
        isAttachToAdd[Integer.parseInt(id)] = false;
      }
      
      num = 0;
      BooleanQuery query = new BooleanQuery();
      for (int i = 0; i <= ipedCase.getLastId(); i++) {
        if (isAttachToAdd[i]) {
          query.add(NumericRangeQuery.newIntRange(IndexItem.ID, i, i, true, true), Occur.SHOULD);
          num++;
        }
        if (num == 1000 || (num > 0 && i == ipedCase.getLastId())) {
          IPEDSearcher searchAttachs = new IPEDSearcher(ipedCase, query);
    	  LuceneSearchResult attachs = searchAttachs.luceneSearch();
          insertIntoProcessQueue(attachs, false);
          query = new BooleanQuery();
          num = 0;
        }
      }
    }
  }

  private void insertIntoProcessQueue(LuceneSearchResult result, boolean treeNode) throws Exception {

    for (int docID : result.getLuceneIds()) {
      Document doc = ipedCase.getReader().document(docID);

      String value = doc.get(IndexItem.LENGTH);
      Long len = null;
      if (value != null && !value.isEmpty()) {
        len = Long.valueOf(value);
      }

      if (listOnly) {
        caseData.incDiscoveredEvidences(1);
        if (!treeNode) {
          caseData.incDiscoveredVolume(len);
        }
        continue;
      }

      EvidenceFile evidence = new EvidenceFile();
      evidence.setName(doc.get(IndexItem.NAME));

      evidence.setLength(len);
      if (treeNode) {
        evidence.setSumVolume(false);
      }
      
      //TODO obter source corretamente
      DataSource dataSource = new DataSource(null);
      dataSource.setUUID(doc.get(IndexItem.EVIDENCE_UUID));
      evidence.setDataSource(dataSource);

      int id = Integer.valueOf(doc.get(IndexItem.ID));
      evidence.setId(id);

      if (!treeNode) {
        for (int labelId : state.getLabelIds(id)) {
          selectedLabels.add(labelId);
        }
      }

      evidence.setLabels(state.getLabels(id));

      value = doc.get(IndexItem.PARENTID);
      if (value != null) {
        evidence.setParentId(Integer.valueOf(value));
      }

      value = doc.get(IndexItem.PARENTIDs);
      ArrayList<Integer> parents = new ArrayList<Integer>();
      for (String parent : value.split(" ")) {
        if (!parent.isEmpty()) {
          parents.add(Integer.parseInt(parent));
        }
      }
      evidence.addParentIds(parents);

      value = doc.get(IndexItem.TYPE);
      if (value != null) {
        evidence.setType(new GenericFileType(value));
      }

      value = doc.get(IndexItem.CATEGORY);
      for (String category : value.split(CategoryTokenizer.SEPARATOR + "")) {
        evidence.addCategory(category);
      }

      value = doc.get(IndexItem.ACCESSED);
      if (!value.isEmpty()) {
        evidence.setAccessDate(DateUtil.stringToDate(value));
      }

      value = doc.get(IndexItem.CREATED);
      if (!value.isEmpty()) {
        evidence.setCreationDate(DateUtil.stringToDate(value));
      }

      value = doc.get(IndexItem.MODIFIED);
      if (!value.isEmpty()) {
        evidence.setModificationDate(DateUtil.stringToDate(value));
      }

      evidence.setPath(doc.get(IndexItem.PATH));
      
      value = doc.get(IndexItem.EXPORT);
      if (value != null && !value.isEmpty() && !treeNode) {
        evidence.setFile(Util.getRelativeFile(basePath, value));
      } else {
        value = doc.get(IndexItem.SLEUTHID);
        if (value != null && !value.isEmpty() && !treeNode) {
          evidence.setSleuthId(Integer.valueOf(value));
          evidence.setSleuthFile(sleuthCase.getContentById(Long.valueOf(value)));
        }
      }

      if (treeNode) {
        evidence.setExtraAttribute(IndexItem.TREENODE, "true");
        evidence.setAddToCase(false);
      }

      String mimetype = doc.get(IndexItem.CONTENTTYPE);
      if (mimetype != null) {
        evidence.setMediaType(MediaType.parse(mimetype));
      }

      evidence.setTimeOut(Boolean.parseBoolean(doc.get(IndexItem.TIMEOUT)));

      value = doc.get(IndexItem.HASH);
      if (value != null) {
        value = value.toUpperCase();
        evidence.setHash(value);

        if (!value.isEmpty()) {
          File viewFile = Util.findFileFromHash(new File(indexDir.getParentFile(), "view"), value);
          if (viewFile != null) {
            evidence.setViewFile(viewFile);
          }

          //Copia resultado prévio do OCR
          String ocrPrefix = OCRParser.TEXT_DIR + "/" + value.charAt(0) + "/" + value.charAt(1);
          File ocrSrc = new File(indexDir.getParentFile(), ocrPrefix);
          File ocrDst = new File(output, ocrPrefix);
          if (ocrSrc.exists()) {
            ocrDst.mkdirs();
            for (String name : ocrSrc.list()) {
              if (name.equals(value + ".txt") || name.startsWith(value + "-child")) {
                IOUtil.copiaArquivo(new File(ocrSrc, name), new File(ocrDst, name));
              }
            }
          }

          //Copia miniaturas
          File thumbSrc = Util.getFileFromHash(new File(indexDir.getParentFile(), ImageThumbTask.thumbsFolder), value, "jpg");
          File thumbDst = Util.getFileFromHash(new File(output, ImageThumbTask.thumbsFolder), value, "jpg");
          if (thumbSrc.exists()) {
            thumbDst.getParentFile().mkdirs();
            IOUtil.copiaArquivo(thumbSrc, thumbDst);
          }
        }
      }

      String[] hashes = {"md5", "sha-1", "sha-256", "sha-512", HashTask.EDONKEY};
      for (String hash : hashes) {
        value = doc.get(hash);
        if (value != null) {
          evidence.setExtraAttribute(hash, value);
        }
      }

      //armazena metadados de emails, necessário para emails de PST
      if(OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype))	
        for (String key : ColumnsManager.email) {
          value = doc.get(key);
          if (value != null) {
            evidence.getMetadata().set(key, value);
          }
        }

      value = doc.get(IndexItem.DELETED);
      evidence.setDeleted(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.ISDIR);
      evidence.setIsDir(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.CARVED);
      evidence.setCarved(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.SUBITEM);
      evidence.setSubItem(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.HASCHILD);
      evidence.setHasChildren(Boolean.parseBoolean(value));

      value = doc.get(IndexItem.OFFSET);
      if (value != null) {
        evidence.setFileOffset(Long.parseLong(value));
      }

      value = doc.get(IndexItem.ISROOT);
      if (value != null) {
        evidence.setRoot(true);
      }

      caseData.addEvidenceFile(evidence);
    }

  }

}
