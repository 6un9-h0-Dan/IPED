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
package dpf.sp.gpinf.indexer.search;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.highlight.TextFragment;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.DateUtil;

public class ResultTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;
	
	public static String[] fields;
	
	private int coldWidths[] = {55, 20, 35, 100, 200, 50, 100, 60, 150, 155, 155, 155, 250, 2000};
	
	public static String[] fixedCols = { "", "", "%", "Marcador"};
	private static String[] columnNames = {};
	
	public void initCols(){
		
		updateCols();
		
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				App.get().resultsModel.fireTableStructureChanged();
				App.get().resultsTable.getColumnModel().getColumn(2).setCellRenderer(new ProgressCellRenderer());
				for(int i = 0; i < coldWidths.length; i++)
					App.get().resultsTable.getColumnModel().getColumn(i).setPreferredWidth(coldWidths[i]);
			}
		});
		
	}
	
	public void updateCols(){
		
		ArrayList<String> cols = new ArrayList<String>();
		for(String col : fixedCols)
			cols.add(col);
		
		fields = ColumnsManager.getInstance().getLoadedCols();
		for(String col : fields)
			cols.add(col.substring(0, 1).toUpperCase() + col.substring(1));
		
		columnNames = cols.toArray(new String[0]);
	}

	
	private SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z");
	
	public ResultTableModel(){
		super();
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	 

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public int getRowCount() {
		return App.get().results.length;
	}

	@Override
	public String getColumnName(int col) {
		if (col == 0)
			return String.valueOf(App.get().results.length);
		else
			return columnNames[col];
	}
	
	public void updateLengthHeader(long mb){
		for(int i = 0; i < columnNames.length; i++)
			if(IndexItem.LENGTH.equalsIgnoreCase(columnNames[i])){
				int col = App.get().resultsTable.convertColumnIndexToView(i);
				if(mb == -1){
					App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(
							columnNames[i] + " (...)");
				}else
					App.get().resultsTable.getColumnModel().getColumn(col).setHeaderValue(
							columnNames[i] + " (" + NumberFormat.getNumberInstance().format(mb) + "MB)");
			}
				
	}

	@Override
	public boolean isCellEditable(int row, int col) {
		if (col == 1)
			return true;
		else
			return false;
	}
	
	App app = App.get();

	@Override
	public void setValueAt(Object value, int row, int col) {
		app.marcadores.setValueAtId(value, app.ids[app.results.docs[row]], col, true);

	}

	@Override
	public Class<?> getColumnClass(int c) {
		if (c == 1)
			return Boolean.class;
		else if (columnNames[c].equalsIgnoreCase(IndexItem.LENGTH))
			return Integer.class;
		else
			return String.class;
	}

	private Document doc;
	private int lastDocRead = -1;

	@Override
	public Object getValueAt(int row, int col) {
		String value;
		if (col == 0)
			value = String.valueOf(App.get().resultsTable.convertRowIndexToView(row) + 1);

		else if (col == 1)
			return app.marcadores.selected[app.ids[app.results.docs[row]]];

		else if (col == 2)
			return app.results.scores[row];

		else if (col == 3)
			return app.marcadores.getLabels(app.ids[app.results.docs[row]]);
		
		else
			try {
				int docId = App.get().results.docs[row];
				if (App.get().results.docs[row] != lastDocRead)
					doc = App.get().searcher.doc(docId);
				lastDocRead = App.get().results.docs[row];

				int fCol = col - fixedCols.length;
				String field = fields[fCol];
				value = doc.get(field);
				if(value == null)
					value = "";
				if(value.isEmpty())
					return value;
				
				try{
					value = df.format(DateUtil.stringToDate(value));
					return value;
					
				}catch(Exception e){}
				
				if(field.equals(IndexItem.LENGTH))
					value = NumberFormat.getNumberInstance().format(Long.valueOf(value));
				
				else if(field.equals(IndexItem.CATEGORY))
					value = value.replace("" + CategoryTokenizer.SEPARATOR, " | ");
				
				if (field.equals(IndexItem.NAME)) {
					TextFragment[] fragments = TextHighlighter.getHighlightedFrags(false, value, field, 0);
					if (fragments[0].getScore() > 0)
						value = "<html><nobr>" + fragments[0].toString() + "</html>";
				}

			} catch (Exception e) {
				e.printStackTrace();
				return "ERRO";
			}

		return value;

	}

}
