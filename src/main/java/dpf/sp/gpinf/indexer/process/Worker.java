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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriter;
import org.apache.tika.config.TikaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.TaskInstaller;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.EvidenceFile;

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

  private static Logger LOGGER = LoggerFactory.getLogger(Worker.class);

  private static String workerNamePrefix = "Worker-";
  
  public IndexWriter writer;
  String baseFilePath;

  //TODO mover para tarefas que usam estes objetos
  public TikaConfig config;

  public volatile AbstractTask runningTask;
  public List<AbstractTask> tasks = new ArrayList<AbstractTask>();
  public AbstractTask firstTask;
  public volatile int itensBeingProcessed = 0;
  
  public enum STATE {
	    RUNNING, PAUSING, PAUSED 
  }
  
  public volatile STATE state = STATE.RUNNING;

  public Manager manager;
  public Statistics stats;
  public File output;
  public CaseData caseData;
  public volatile Exception exception;
  public volatile EvidenceFile evidence;

  public Worker(int k, CaseData caseData, IndexWriter writer, File output, Manager manager) throws Exception {
    super(new ThreadGroup("ProcessingThreadGroup-" + k), workerNamePrefix + k);
    this.caseData = caseData;
    this.writer = writer;
    this.output = output;
    this.manager = manager;
    this.stats = manager.stats;
    baseFilePath = output.getParentFile().getAbsolutePath();

    if (k == 0) {
      LOGGER.info("Inicializando Tika");
    }

    config = TikaConfig.getDefaultConfig();
    
    TaskInstaller taskInstaller = new TaskInstaller();
    taskInstaller.installProcessingTasks(this);
    doTaskChaining();
    initTasks();

  }
  
  private void doTaskChaining() {
    firstTask = tasks.get(0);
    for (int i = 0; i < tasks.size() - 1; i++) {
      tasks.get(i).setNextTask(tasks.get(i + 1));
    }
  }

  private void initTasks() throws Exception {
    for (AbstractTask task : tasks) {
      if (this.getName().equals(workerNamePrefix + 0)) {
        LOGGER.info("Inicializando " + task.getClass().getSimpleName());
      }
      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Inicializando " + task.getClass().getSimpleName() + "...");
      task.init(Configuration.properties, new File(Configuration.configPath, "conf"));
    }

  }

  private void finishTasks() throws Exception {
    for (AbstractTask task : tasks) {
      task.finish();
    }
  }

  public void finish() throws Exception {
    this.interrupt();
    finishTasks();
  }
  
  public void processNextQueue() {
	this.interrupt();
  }

  /**
   * Alguns itens ainda não tem um File setado, como report do FTK1.
   *
   * @param evidence
   */
  private void checkFile(EvidenceFile evidence) {
    String filePath = evidence.getFileToIndex();
    if (evidence.getFile() == null && !filePath.isEmpty()) {
      File file = Util.getRelativeFile(baseFilePath, filePath);
      evidence.setFile(file);
      evidence.setLength(file.length());
    }
  }

  /**
   * Processa o item em todas as tarefas instaladas. Caso ocorra exceção não esperada, armazena
   * exceção para abortar processamento.
   *
   * @param evidence Item a ser processado
   */
  public void process(EvidenceFile evidence) {

    EvidenceFile prevEvidence = this.evidence;
    if (!evidence.isQueueEnd()) {
      this.evidence = evidence;
    }

    try {

      LOGGER.debug("{} Processando {} ({} bytes)", getName(), evidence.getPath(), evidence.getLength());

      checkFile(evidence);

      //Loop principal que executa cada tarefa de processamento
			/*for(AbstractTask task : tasks)
       if(!evidence.isToIgnore()){
       processTask(evidence, task);
       }
       */
      firstTask.processAndSendToNextTask(evidence);

    } catch (Throwable t) {
      //ABORTA PROCESSAMENTO NO CASO DE QQ OUTRO ERRO
      if (exception == null) {
    	  if(t instanceof IPEDException)
    		  exception = (IPEDException)t;
    	  else{
    		  exception = new Exception(this.getName() + " Erro durante processamento de " + evidence.getPath() + " (" + evidence.getLength() + "bytes)");
    		  exception.initCause(t);
    	  }
      }

    }

    this.evidence = prevEvidence;

  }

  /**
   * Processa ou enfileira novo item criado (subitem de zip, pst, carving, etc).
   *
   * @param evidence novo item a ser processado.
   */
  public void processNewItem(EvidenceFile evidence) {
    caseData.incDiscoveredEvidences(1);
    // Se a fila está pequena, enfileira
    if (caseData.getItemQueue().size() < 10 * manager.getWorkers().length) {
    	caseData.getItemQueue().addFirst(evidence);
    } // caso contrário processa o item no worker atual
    else {
      long t = System.nanoTime() / 1000;
      process(evidence);
      runningTask.addSubitemProcessingTime(System.nanoTime() / 1000 - t);
    }

  }

  @Override
  public void run() {

    LOGGER.info("{} iniciado.", getName());

    while (!this.isInterrupted() && exception == null) {

      try {
        evidence = null;
        evidence = caseData.getItemQueue().takeFirst();

        if (!evidence.isQueueEnd()) {
          process(evidence);
          
        } else {
          EvidenceFile queueEnd = evidence;
          evidence = null;
          if (manager.numItensBeingProcessed() == 0) {
        	  caseData.getItemQueue().addLast(queueEnd);
            process(queueEnd);
            break;
          } else {
        	  caseData.getItemQueue().addLast(queueEnd);
            if (itensBeingProcessed > 0) {
              process(queueEnd);
            } else {
              Thread.sleep(1000);
            }
          }
        }

      } catch (InterruptedException e) {
    	  if(caseData.getCurrentQueuePriority() == null)
    		  break;
      }
    }

    if (evidence == null) {
      LOGGER.info("{} finalizado.", getName());
    } else {
      LOGGER.info("{} interrompido com {} ({} bytes)", getName(), evidence.getPath(), evidence.getLength());
    }
  }

}
