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
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.GenericFileType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.InicializarBusca;
import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.search.PesquisarIndice;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;

/*
 * Enfileira para processamento os arquivos selecionados via interface de pesquisa de uma indexação anterior.
 */
public class IPEDReader extends DataSourceReader{

	private static Object lock = new Object();
	
	//Referência estática para a JVM não finalizar o objeto que será usado futuramente
	//via referência interna ao JNI para acessar os itens do caso
	static SleuthkitCase sleuthCase;
	
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

		state = (Marcadores) Util.readObject(file.getAbsolutePath());
		indexDir = state.getIndexDir().getCanonicalFile();
		basePath = indexDir.getParentFile().getParentFile().getAbsolutePath();
		String dbPath = basePath + File.separator + SleuthkitReader.DB_NAME;
		
		synchronized (lock) {
			if (new File(dbPath).exists() && sleuthCase == null)
				sleuthCase = SleuthkitCase.openCase(dbPath);
				
			if(App.get().reader == null)
				InicializarBusca.inicializar(indexDir.getAbsolutePath());
		}
		
		Logger.getLogger("org.sleuthkit").setLevel(Level.SEVERE);
		
		selectedLabels = new HashSet<Integer>();
		App.get().marcadores = state;
		PesquisarIndice pesquisa = new PesquisarIndice(new MatchAllDocsQuery());
		SearchResult result = pesquisa.filtrarSelecionados(pesquisa.pesquisar());
		
		insertIntoProcessQueue(result, false);
		
		//Inclui anexos de emails de PST
		CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (!args.getCmdArgs().containsKey("--nopstattachs")){
        	BooleanQuery query = new BooleanQuery();
    		for (int docID : result.docs) {
    			String mimetype = App.get().reader.document(docID).get(IndexItem.CONTENTTYPE);
    			if(OutlookPSTParser.OUTLOOK_MSG_MIME.equals(mimetype))
    				query.add(NumericRangeQuery.newIntRange(IndexItem.PARENTID, App.get().ids[docID], App.get().ids[docID], true, true), Occur.SHOULD);
    		}
    		for (int docID : result.docs)
    			query.add(NumericRangeQuery.newIntRange(IndexItem.ID, App.get().ids[docID], App.get().ids[docID], true, true), Occur.MUST_NOT);
    		
    		PesquisarIndice searchPSTAttachs = new PesquisarIndice(query);
    		SearchResult attachs = searchPSTAttachs.pesquisar();
    		insertIntoProcessQueue(attachs, false);
        }
        
        //Inclui pais para visualização em árvore
        BooleanQuery query = new BooleanQuery();
        for (int docID : result.docs) {
            String parentIds = App.get().reader.document(docID).get(IndexItem.PARENTIDs);
            for(String parentId : parentIds.split(" "))
                query.add(NumericRangeQuery.newIntRange(IndexItem.ID, Integer.parseInt(parentId), Integer.parseInt(parentId), true, true), Occur.SHOULD);
            
        }
        for (int docID : result.docs)
            query.add(NumericRangeQuery.newIntRange(IndexItem.ID, App.get().ids[docID], App.get().ids[docID], true, true), Occur.MUST_NOT);
        
        PesquisarIndice searchParents = new PesquisarIndice(query, true);
        result = searchParents.pesquisar();
        insertIntoProcessQueue(result, true);
		
		
		if(!listOnly){
			for(int labelId : state.getLabelMap().keySet().toArray(new Integer[0]))
				if(!selectedLabels.contains(labelId))
					state.delLabel(labelId);
			
			state.resetAndSetIndexDir(new File(output, "index"));
			state.saveState(new File(output, Marcadores.STATEFILENAME));
		}

		if (!listOnly)
			App.get().destroy();
		
		return 0;

	}
	
	private void insertIntoProcessQueue(SearchResult result, boolean treeNode) throws Exception {
		
		for (int docID : result.docs){
			Document doc = App.get().reader.document(docID);

			String value = doc.get(IndexItem.LENGTH);
			Long len = null;
			if (value != null && !value.isEmpty())
				len = Long.valueOf(value);
				
			if (listOnly){
				caseData.incDiscoveredEvidences(1);
				if(!treeNode)
				    caseData.incDiscoveredVolume(len);
				continue;
			}
			
			EvidenceFile evidence = new EvidenceFile();
			evidence.setName(doc.get(IndexItem.NAME));
			
			evidence.setLength(len);
			if(treeNode)
			    evidence.setSumVolume(false);
			
			int id = Integer.valueOf(doc.get(IndexItem.ID));
			evidence.setId(id);
			
			for(int labelId : state.getLabelIds(id))
				selectedLabels.add(labelId);
			
			evidence.setLabels(state.getLabels(id));

			value = doc.get(IndexItem.PARENTID);
			if (value != null)
				evidence.setParentId(value);
			
			value = doc.get(IndexItem.PARENTIDs);
			ArrayList<Integer> parents = new ArrayList<Integer>();
			for(String parent : value.split(" "))
			    if(!parent.isEmpty())
			        parents.add(Integer.parseInt(parent));
			evidence.addParentIds(parents);

			value = doc.get(IndexItem.TYPE);
			if (value != null)
				evidence.setType(new GenericFileType(value));

			value = doc.get(IndexItem.CATEGORY);
			for(String category : value.split(CategoryTokenizer.SEPARATOR + ""))
				evidence.addCategory(category);
			

			value = doc.get(IndexItem.ACCESSED);
			if (!value.isEmpty())
				evidence.setAccessDate(DateUtil.stringToDate(value));

			value = doc.get(IndexItem.CREATED);
			if (!value.isEmpty())
				evidence.setCreationDate(DateUtil.stringToDate(value));

			value = doc.get(IndexItem.MODIFIED);
			if (!value.isEmpty())
				evidence.setModificationDate(DateUtil.stringToDate(value));

			evidence.setPath(doc.get(IndexItem.PATH));

			value = doc.get(IndexItem.EXPORT);
			if (value != null && !value.isEmpty() && !treeNode)
				evidence.setFile(Util.getRelativeFile(basePath, value));

			else {
				value = doc.get(IndexItem.SLEUTHID);
				if (value != null && !value.isEmpty() && !treeNode) {
					evidence.setSleuthId(value);
					evidence.setSleuthFile(sleuthCase.getContentById(Long.valueOf(value)));
				}
			}
			
			if(treeNode){
				evidence.setExtraAttribute(IndexItem.TREENODE, "true");
				evidence.setAddToCase(false);
			}

			value = doc.get(IndexItem.CONTENTTYPE);
			if (value != null)
				evidence.setMediaType(MediaType.parse(value));

			evidence.setTimeOut(Boolean.parseBoolean(doc.get(IndexItem.TIMEOUT)));

			value = doc.get(IndexItem.HASH);
			if (value != null){
				value = value.toUpperCase();
				evidence.setHash(value);
				
				if(!value.isEmpty()){
					File viewFile = Util.findFileFromHash(new File(indexDir.getParentFile(), "view"), value);
					if(viewFile != null)
						evidence.setViewFile(viewFile);
					
					//Copia resultado prévio do OCR
					String ocrPrefix = OCRParser.TEXT_DIR + "/" + value.charAt(0) + "/" + value.charAt(1);
		            File ocrDir = new File(indexDir.getParentFile(), ocrPrefix);
		            File destDir = new File(output, ocrPrefix);
		            if(ocrDir.exists()){
		                destDir.mkdirs();
		                for(String name : ocrDir.list())
		                    if(name.equals(value + ".txt") || name.startsWith(value + "-child"))
		                        IOUtil.copiaArquivo(new File(ocrDir, name), new File(destDir, name));
		            }
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
			if(value != null)
				evidence.setFileOffset(Long.parseLong(value));
			
			value = doc.get(IndexItem.ISROOT);
			if(value != null)
			    evidence.setRoot(true);

			evidence.setToExtract(true);

			caseData.addEvidenceFile(evidence);
		}
		
	}

}
