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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.lucene.index.IndexWriter;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.util.IOUtil;

/**
 * Responsável por retirar um item da fila e enviá-lo para cada tarefa de processamento instalada:
 * análise de assinatura, hash, expansão de itens, indexação, carving, etc.
 * 
 * São executados vários Workers paralelamente. Cada Worker possui instâncias próprias das tarefas,
 * para evitar problemas de concorrência.
 * 
 * Caso haja uma exceção não esperada, ela é armazenada para que possa ser detectada pelo manager.
 */
public class Worker extends Thread {

	LinkedBlockingDeque<EvidenceFile> evidences;

	public IndexWriter writer;
	String baseFilePath;
	public boolean containsReport;

	public IndexerDefaultParser autoParser;
	public Detector detector;
	public TikaConfig config;
	
	public volatile AbstractTask runningTask;
	public List<AbstractTask> tasks = new ArrayList<AbstractTask>();
	AbstractTask firstTask;

	public Manager manager;
	public Statistics stats;
	public File output;
	public CaseData caseData;
	public volatile Exception exception;
	public volatile EvidenceFile evidence;

	public static class IdLenPair {
		int id, length;

		public IdLenPair(int id, long len) {
			this.id = id;
			this.length = (int) (len / 1000);
		}

	}

	public static void resetStaticVariables() {
		IndexerDefaultParser.parsingErrors = 0;
		ParsingReader.threadPool = Executors.newCachedThreadPool();
	}

	public Worker(int k, CaseData caseData, IndexWriter writer, File output, Manager manager) throws Exception {
		super("Worker-" + k);
		this.caseData = caseData;
		this.evidences = caseData.getEvidenceFiles();
		this.containsReport = caseData.containsReport();
		this.writer = writer;
		this.output = output;
		this.manager = manager;
		this.stats = manager.stats;
		baseFilePath = output.getParentFile().getAbsolutePath();
		
		config = TikaConfig.getDefaultConfig();
		detector = config.getDetector();

		autoParser = new IndexerDefaultParser();
		autoParser.setFallback((Parser) Configuration.fallBackParser.newInstance());
		autoParser.setErrorParser((Parser) Configuration.errorParser.newInstance());
		
		new TaskInstaller().installProcessingTasks(this);
		initTasks();

	}
	
	private void initTasks() throws Exception{
		for(AbstractTask task : tasks)
			task.init(Configuration.properties, new File(Configuration.configPath, "conf"));
	}
	
	private void finishTasks() throws Exception{
		for(AbstractTask task : tasks)
			task.finish();
	}
	
	public void finish() throws Exception{
		this.interrupt();
		finishTasks();
	}
	
	/**
	 * Alguns itens ainda nÃ£o tem um File setado, como report do FTK1.
	 * 
	 * @param evidence
	 */
	private void checkFile(EvidenceFile evidence){
		String filePath = evidence.getFileToIndex();
		if (evidence.getFile() == null && !filePath.isEmpty()) {
			File file = IOUtil.getRelativeFile(baseFilePath, filePath);
			evidence.setFile(file);
			evidence.setLength(file.length());
		}
	}
	

	/**
	 * Processa o item em todas as tarefas instaladas. Caso ocorra exceÃ§Ã£o nÃ£o
	 * esperada, armazena exceÃ§Ã£o para abortar processamento.
	 * 
	 * @param evidence Item a ser processado
	 */
	public void process(EvidenceFile evidence) {
		
		EvidenceFile prevEvidence = this.evidence;
		this.evidence = evidence;
		
		try {

			if (IndexFiles.getInstance().verbose)
				System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " Indexando " + evidence.getPath());

			checkFile(evidence);
			
			//Loop principal que executa cada tarefa de processamento
			/*for(AbstractTask task : tasks)
				if(!evidence.isToIgnore()){
					processTask(evidence, task);
				}
			*/
			processTask(evidence, firstTask);
			
			
			// ESTATISTICAS
			stats.incProcessed();
			if ((!evidence.isSubItem() && !evidence.isCarved()) || ItemProducer.indexerReport) {
				stats.incActiveProcessed();
				Long len = evidence.getLength();
				if(len == null)
					len = 0L;
				stats.addVolume(len);
			}

		} catch (Throwable t) {	
			//ABORTA PROCESSAMENTO NO CASO DE QQ OUTRO ERRO
			if (exception == null) {
				exception = new Exception(this.getName() + " Erro durante processamento de " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
				exception.initCause(t);
			}

		}

		this.evidence = prevEvidence;

	}
	
	/**
	 * Processa o item em determinada tarefa. Caso ocorra timeout, o item Ã© reprocessado
	 * na tarefa com um parser seguro, sem risco de novo timeout.
	 * 
	 * @param evidence Item a ser procesado
	 * @param task Tarefa que serÃ¡ executada sobre o item.
	 * @throws Exception Se ocorrer erro inesperado.
	 */
	private void processTask(EvidenceFile evidence, AbstractTask task) throws Exception{
		AbstractTask prevTask = runningTask;
		runningTask = task;
		try {
			task.processAndSendToNextTask(evidence);
			
		} catch (TimeoutException e) {
			System.out.println(new Date() + "\t[ALERT]\t" + this.getName() + " TIMEOUT ao processar " + evidence.getPath() + " (" + evidence.getLength() + "bytes)\t" + e);
			stats.incTimeouts();
			evidence.setTimeOut(true);
			processTask(evidence, task);

		}catch (Throwable t) {
			//Ignora arquivos recuperados e corrompidos
			if(t.getCause() instanceof TikaException && evidence.isCarved()){
				stats.incCorruptCarveIgnored();
				//System.out.println(new Date() + "\t[AVISO]\t" + this.getName() + " " + "Ignorando arquivo recuperado corrompido " + evidence.getPath() + " (" + length + "bytes)\t" + t.getCause());
				evidence.setToIgnore(true);
				if(evidence.isSubItem()){
					evidence.getFile().delete();
				}
				
			}else
				throw t;
			
		}
		runningTask = prevTask;
	}
	
	/**
	 * Processa ou enfileira novo item criado (subitem de zip, pst, carving, etc).
	 * 
	 * @param evidence novo item a ser processado.
	 */
	public void processNewItem(EvidenceFile evidence){
		caseData.incDiscoveredEvidences(1);
		// Se nÃ£o hÃ¡ item na fila, enfileira para outro worker processar
		if (caseData.getEvidenceFiles().size() == 0)
			caseData.getEvidenceFiles().addFirst(evidence);
		// caso contrÃ¡rio processa o item no worker atual
		else
			process(evidence);
	}

	@Override
	public void run() {

		System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " iniciada.");
		
		while (!this.isInterrupted() && exception == null) {

			try {
				evidence = null;
				evidence = evidences.takeFirst();
				process(evidence);

			} catch (InterruptedException e) {
				break;
			}
		}

		if (evidence == null)
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " finalizada.");
		else
			System.out.println(new Date() + "\t[INFO]\t" + this.getName() + " interrompida com " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
	}

}
