package dpf.sp.gpinf.indexer.process;

import java.util.List;

import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.CarveTask;
import dpf.sp.gpinf.indexer.process.task.CheckDuplicateTask;
import dpf.sp.gpinf.indexer.process.task.ComputeHashTask;
import dpf.sp.gpinf.indexer.process.task.ComputeSignatureTask;
import dpf.sp.gpinf.indexer.process.task.ExpandContainerTask;
import dpf.sp.gpinf.indexer.process.task.ExportCSVTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.IndexTask;
import dpf.sp.gpinf.indexer.process.task.SetCategoryTask;
import dpf.sp.gpinf.indexer.process.task.SetTypeTask;

public class TaskInstaller {

	public void installProcessingTasks(Worker worker) throws Exception{
		
		List<AbstractTask> tasks = worker.tasks;
	
		tasks.add(new ComputeSignatureTask(worker));
		tasks.add(new SetTypeTask(worker));
		tasks.add(new SetCategoryTask(worker));
		tasks.add(new ComputeHashTask(worker));
		tasks.add(new ExportCSVTask(worker));
		tasks.add(new CheckDuplicateTask(worker));
		tasks.add(new ExpandContainerTask(worker));
		tasks.add(new ExportFileTask(worker));
		//Carving precisa ficar apos exportação (devido a rename que muda a referencia)
		//e antes da indexação (pois pode setar propriedade hasChildren no pai)
		tasks.add(new CarveTask(worker));
		tasks.add(new IndexTask(worker));
		
	}
	
}