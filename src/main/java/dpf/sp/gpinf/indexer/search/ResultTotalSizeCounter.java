package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.apache.lucene.search.IndexSearcher;

import dpf.sp.gpinf.indexer.process.IndexItem;

public class ResultTotalSizeCounter {

	private static volatile Thread lastCounter;

	public void countVolume(final SearchResult result) {

		if (lastCounter != null)
			lastCounter.interrupt();

		Thread counter = new Thread() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().resultsModel.updateLengthHeader(-1);
						App.get().resultsTable.getTableHeader().repaint();
					}
				});
				long volume = 0;
				IndexSearcher searcher = App.get().appCase.searcher;
				Set<String> fieldsToLoad = Collections.singleton(IndexItem.LENGTH);
				for (int doc : result.docs) {
					try {
						String len = searcher.doc(doc, fieldsToLoad).get(IndexItem.LENGTH);
						if (len != null && !len.isEmpty())
							volume += Long.valueOf(len);

					} catch (IOException e) {
						// e.printStackTrace();
					}

					if (Thread.currentThread().isInterrupted())
						return;
				}
				volume = volume / (1024 * 1024);
				final long fVol = volume;

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().resultsModel.updateLengthHeader(fVol);
						App.get().resultsTable.getTableHeader().repaint();
					}
				});

			}
		};

		counter.start();
		lastCounter = counter;

	}
}
