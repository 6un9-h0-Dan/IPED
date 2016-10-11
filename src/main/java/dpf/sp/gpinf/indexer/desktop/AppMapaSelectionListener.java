package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.util.HashSet;
import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MapSelectionListener;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;

public class AppMapaSelectionListener implements MapSelectionListener {

	@Override
	public void OnSelect(String[] mids) {
		int pos=0;
        JTable t = App.get().getResultsTable();
        org.apache.lucene.document.Document doc = null;
        LuceneSearchResult results = App.get().getResults();
        
        HashSet<String> columns = new HashSet<String>();
        columns.add("id");

        t.getSelectionModel().setValueIsAdjusting(true);
        for (int i = 0; i < results.getLength(); i++) {
        	try {
        		pos = -1;
    			doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[i], columns);
    			for (int j = 0; j < mids.length; j++) {
    	        	if(mids[j].equals(doc.get("id"))){
    	        		pos = i;
    	        		break;
    	        	}
    			}
    			if(pos>=0){
        	        pos = t.convertRowIndexToView(pos);
        	        t.addRowSelectionInterval(pos, pos);
        	        //t.changeSelection(pos, 1, false, false);
    			}
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
        }
        
        t.getSelectionModel().setValueIsAdjusting(false);
	}

}
