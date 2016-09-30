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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.util.HashSet;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.QueryBuilder;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;

public class PesquisarIndice extends CancelableWorker<SearchResult, Object> {

	private static Logger LOGGER = LoggerFactory.getLogger(PesquisarIndice.class);
	
	private static SearchResult allItemsCache;
	
	volatile static int numFilters = 0;
	ProgressDialog progressDialog;
	
	String queryText;
	IPEDSearcher searcher;

	public PesquisarIndice(String queryText) {
		this.queryText = queryText;
		searcher = new IPEDSearcher(App.get().appCase, queryText);
	}
	
	public PesquisarIndice(Query query) {
		searcher = new IPEDSearcher(App.get().appCase, query);
	}
	
	public void applyUIQueryFilters(){
		try {
			searcher.setQuery(getQueryWithUIFilter(queryText));
			
		} catch (ParseException | QueryNodeException e) {
			e.printStackTrace();
		}
	}
	
	public SearchResult pesquisar() throws Exception {
		return searcher.pesquisar();
	}

	private Query getQueryWithUIFilter(String texto) throws ParseException, QueryNodeException {
		
		numFilters = 0;
		if (!texto.trim().isEmpty())
			numFilters++;
		
		if(App.get().filtro.getSelectedIndex() > 1){
			String filter = App.get().filtro.getSelectedItem().toString();
			filter =  App.get().filterManager.getFilterExpression(filter);
			if (texto.trim().isEmpty())
				texto = filter;
			else
				texto = "(" + filter  + ") && (" + texto + ")";
			numFilters++;
		}
		
		Query result = new QueryBuilder(App.get().appCase).getQuery(texto);
		
		if(App.get().categoryListener.query != null){
			BooleanQuery boolQuery = new BooleanQuery();
			boolQuery.add(App.get().categoryListener.query, Occur.MUST);
			boolQuery.add(result, Occur.MUST);
			result = boolQuery;
			numFilters++;
		}
		
		if (!App.get().appCase.isFTKReport()) {
		      Query treeQuery = App.get().treeListener.treeQuery;
		      if (App.get().recursiveTreeList.isSelected())
		    	  treeQuery = App.get().treeListener.recursiveTreeQuery;
		
		      if (treeQuery != null) {
		        BooleanQuery boolQuery = new BooleanQuery();
		        boolQuery.add(result, Occur.MUST);
		        boolQuery.add(treeQuery, Occur.MUST);
		        result = boolQuery;
		        numFilters++;
		      }
		}  
		
		return result;
  }


	@Override
	public SearchResult doInBackground() {
		
		synchronized(this.getClass()){
			
			if (this.isCancelled())
				return null;
			
			SearchResult result = null;
			try {
				progressDialog = new ProgressDialog(App.get(), this, true, 0, ModalityType.TOOLKIT_MODAL);
					
				Query q = searcher.getQuery();
				if(q instanceof MatchAllDocsQuery && allItemsCache != null)
					result = allItemsCache;
				else{
					result = searcher.pesquisar();
					if(q instanceof MatchAllDocsQuery && allItemsCache == null)
						allItemsCache = result.clone();
				}

				String filtro = App.get().filtro.getSelectedItem().toString();
				if (filtro.equals(App.FILTRO_SELECTED)){
					result = App.get().appCase.getMarcadores().filtrarSelecionados(result, App.get().appCase);
					numFilters++;
				}
				
				HashSet<String> bookmarkSelection = (HashSet<String>)App.get().bookmarksListener.selection.clone();
				if(!bookmarkSelection.isEmpty() && !bookmarkSelection.contains(BookmarksTreeModel.ROOT)){
					numFilters++;
					if(bookmarkSelection.contains(BookmarksTreeModel.NO_BOOKMARKS)){
						if(bookmarkSelection.size() == 1)
							result = App.get().appCase.getMarcadores().filtrarSemMarcadores(result, App.get().appCase);
						else{
							bookmarkSelection.remove(BookmarksTreeModel.NO_BOOKMARKS);
							result = App.get().appCase.getMarcadores().filtrarSemEComMarcadores(result, bookmarkSelection, App.get().appCase);
						}
					}else
						result = App.get().appCase.getMarcadores().filtrarMarcadores(result, bookmarkSelection, App.get().appCase);
					
				}

				App.get().getSearchParams().highlightTerms = new QueryBuilder(App.get().appCase).getQueryStrings(queryText);
				App.get().setQuery(searcher.getQuery());

			} catch (Throwable e) {
				e.printStackTrace();
				return new SearchResult(0);
				
			}
			
			return result;
		}
		
	}

	@Override
	public void done() {
		
		if(numFilters > 1)
			App.get().multiFilterAlert.setVisible(true);
		else
			App.get().multiFilterAlert.setVisible(false);
		
		if (!this.isCancelled())
			try {
				App.get().results = this.get();
				new ResultTotalSizeCounter().countVolume(App.get().results);
				App.get().resultsTable.getColumnModel().getColumn(0).setHeaderValue(this.get().getLength());
				App.get().resultsTable.getTableHeader().repaint();
				if(App.get().results.getLength() < 1 << 24 && App.get().resultsTable.getRowSorter() != null){
					App.get().resultsTable.getRowSorter().allRowsChanged();
					App.get().resultsTable.getRowSorter().setSortKeys(App.get().resultSortKeys);
				}else{
					App.get().resultsModel.fireTableDataChanged();
					App.get().galleryModel.fireTableStructureChanged();
				}
					
			} catch (Exception e) {
				e.printStackTrace();
			}
		if(progressDialog != null)
			progressDialog.close();

	}

  @Override
	public boolean doCancel(boolean mayInterruptIfRunning) {
		
		LOGGER.error("Pesquisa cancelada!");
		searcher.cancel();
		try {
			ResultTableRowSorter.resetComparators();
			App.get().appCase.reopen();

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return cancel(mayInterruptIfRunning);
	}

}
