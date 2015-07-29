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
package dpf.sp.gpinf.indexer.process;

import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Date;
import java.util.List;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.datasource.FTK1ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportProcessor;
import dpf.sp.gpinf.indexer.datasource.FolderTreeProcessor;
import dpf.sp.gpinf.indexer.datasource.IndexerProcessor;
import dpf.sp.gpinf.indexer.datasource.SleuthkitProcessor;

/**
 *  Responsável por instanciar e executar o contador e o produtor de itens do caso
 *  que adiciona os itens a fila de processamento. Podem obter os itens de diversas
 *  fontes de dados: pastas, relatórios do FTK, imagens forenses ou casos do IPED.
 *  
 */  
public class ItemProducer extends Thread {
	
	public static volatile boolean indexerReport = false;

	private final CaseData caseData;
	private final boolean listOnly;
	private List<File> datasources;
	private List<String> caseNames;
	private File output;
	private Manager manager;
	
	private SleuthkitProcessor sleuthkitProcessor;

	ItemProducer(Manager manager, CaseData caseData, boolean listOnly, List<File> datasources, List<String> caseNames, File output) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.caseNames = caseNames;
		this.datasources = datasources;
		this.output = output;
		this.manager = manager;
		if(listOnly)
			indexerReport = false;
	}
	
	public String currentDirectory(){
		if(sleuthkitProcessor != null)
			return sleuthkitProcessor.currentDirectory();
		else
			return null;
	}

	@Override
	public void run() {
		try {
			int caseNameIndex = 0;
			for (File source : datasources) {
				if (Thread.interrupted())
					throw new InterruptedException(Thread.currentThread().getName() + "interrompida.");

				if (!listOnly) {
					IndexFiles.getInstance().firePropertyChange("mensagem", 0, "Processando '" + source.getAbsolutePath() + "'");
					System.out.println(new Date() + "\t[INFO]\t" + "Processando '" + source.getAbsolutePath() + "'");
				}

				int alternativeFiles = 0;
				if ((new File(source, "files")).exists() && FTK3ReportProcessor.bookmarkExists(source)) {
					FTK3ReportProcessor processor = new FTK3ReportProcessor(caseData, output, listOnly);
					alternativeFiles += processor.process(source, caseNames.get(caseNameIndex++));

				} else if ((new File(source, "Export")).exists() && new File(source, "CaseInformation.htm").exists()) {
					FTK1ReportProcessor processor = new FTK1ReportProcessor(caseData, listOnly);
					alternativeFiles += processor.process(source);

				} else if (SleuthkitProcessor.isSupported(source)) {
					sleuthkitProcessor = new SleuthkitProcessor(caseData, output, listOnly);
					sleuthkitProcessor.process(source);

				} else if (IndexerProcessor.isSupported(source)) {
					indexerReport = true;
					IndexerProcessor processor = new IndexerProcessor(caseData, output, listOnly);
					processor.process(source);

				} else {
					FolderTreeProcessor processor = new FolderTreeProcessor(caseData, output, listOnly);
					processor.process(source);

				}

				caseData.incAlternativeFiles(alternativeFiles);
			}
			if (!listOnly) {
			
				 EvidenceFile evidence = new EvidenceFile();
				 evidence.setQueueEnd(true);
				 //caseData.addEvidenceFile(evidence);
				 
			} else {
				IndexFiles.getInstance().firePropertyChange("taskSize", 0, (int)(caseData.getDiscoveredVolume()/1000000));
				System.out.println(new Date() + "\t[INFO]\t" + "Localizados " + caseData.getDiscoveredEvidences() + " itens");
			}

		} catch (Exception e) {
			if (manager.exception == null)
				manager.exception = e;
		}

	}
}
