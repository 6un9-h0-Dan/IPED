package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.ArrayUtils;

import dpf.sp.gpinf.indexer.util.Util;

public class MultiMarcadores implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Map<Integer, Marcadores> map = new HashMap<Integer, Marcadores>();
	
	public MultiMarcadores(List<IPEDSource> sources){
		for(IPEDSource s : sources)
			map.put(s.getSourceId(), s.getMarcador());
	}
	
	public int getTotalSelected(){
		int sum = 0;
		for(Marcadores m : map.values())
			sum += m.getTotalSelected();
		return sum;
	}
	
	public void clearSelected(){
		for(Marcadores m : map.values())
			m.clearSelected();
	}
	
	public void selectAll(){
		for(Marcadores m : map.values())
			m.selectAll();
	}
	
	public boolean isSelected(ItemId item){
		return map.get(item.getSourceId()).isSelected(item.getId());
	}
	
	public void setSelected(boolean value, ItemId item, IPEDSource ipedCase) {
		map.get(item.getSourceId()).setSelected(value, item.getId(), ipedCase);
	}

	public String getLabels(ItemId item) {
		return map.get(item.getSourceId()).getLabels(item.getId());
	}

	public final boolean hasLabel(ItemId item){
		return map.get(item.getSourceId()).hasLabel(item.getId());
	}
	
	public final boolean hasLabel(ItemId item, Set<String> labelNames){
		Marcadores m = map.get(item.getSourceId());
		
		int[] labelIds = new int[labelNames.size()];
	  	int i = 0;
	  	for(String labelName : labelNames)
	  		labelIds[i++] = m.getLabelId(labelName);
	  	
	  	return m.hasLabel(item.getId(), m.getLabelBits(labelIds));
	}
	
	public final boolean hasLabel(ItemId item, String labelName) {
		Marcadores m = map.get(item.getSourceId());
		return m.hasLabel(item.getId(), m.getLabelId(labelName));
	}

	public void addLabel(ArrayList<ItemId> ids, String labelName) {
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = getIdsPerSource(ids);
		for(Integer sourceId : itemsPerSource.keySet()){
			Marcadores m = map.get(sourceId);
			if(m.getLabelId(labelName) == -1)
				System.out.println(m.newLabel(labelName));
			m.addLabel(itemsPerSource.get(sourceId), m.getLabelId(labelName));
		}
	}
	
	private HashMap<Integer, ArrayList<Integer>> getIdsPerSource(ArrayList<ItemId> ids){
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = new HashMap<Integer, ArrayList<Integer>>(); 
		for(ItemId item : ids){
			ArrayList<Integer> items = itemsPerSource.get(item.getSourceId());
			if(items == null){
				items = new ArrayList<Integer>();
				itemsPerSource.put(item.getSourceId(), items);
			}
			items.add(item.getId());
		}
		return itemsPerSource;
	}
	
	public void removeLabel(ArrayList<ItemId> ids, String labelName) {
		HashMap<Integer, ArrayList<Integer>> itemsPerSource = getIdsPerSource(ids);
		for(Integer sourceId : itemsPerSource.keySet()){
			Marcadores m = map.get(sourceId);
			m.removeLabel(itemsPerSource.get(sourceId), m.getLabelId(labelName));
		}

	}

	public void newLabel(String labelName) {
		for(Marcadores m : map.values())
			m.newLabel(labelName);
	}

	public void delLabel(String labelName) {
		for(Marcadores m : map.values()){
			int labelId = m.getLabelId(labelName); 
			m.delLabel(labelId);
		}
	}
	
	public void changeLabel(String oldLabel, String newLabel){
		for(Marcadores m : map.values())
			m.changeLabel(m.getLabelId(oldLabel), newLabel);
	}
	
	public TreeSet<String> getLabelMap(){
		TreeSet<String> labels = new TreeSet<String>();
		for(Marcadores m : map.values())
			labels.addAll(m.getLabelMap().values());
		return labels;
	}
	
	public IPEDResult filtrarMarcadores(IPEDResult result, Set<String> labelNames) throws Exception{
		ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
	  	ArrayList<Float> scores = new ArrayList<Float>();
	  	int i = 0;
	  	for(ItemId item : result.getIds()){
	  		if(this.hasLabel(item, labelNames)){
	  			selectedItems.add(item);
	  			scores.add(result.getScores()[i]);
	  		}
	  		i++;
	  	}
	  	IPEDResult r = new IPEDResult(selectedItems.toArray(new ItemId[0]),
	  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
	  	
		return r;
	  }
	  
	  public IPEDResult filtrarSemEComMarcadores(IPEDResult result, Set<String> labelNames) throws Exception{
		  ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	for(ItemId item : result.getIds()){
		  		if(!this.hasLabel(item) || this.hasLabel(item, labelNames)){
		  			selectedItems.add(item);
		  			scores.add(result.getScores()[i]);
		  		}
		  		i++;
		  	}
		  	IPEDResult r = new IPEDResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	  
	  public IPEDResult filtrarSemMarcadores(IPEDResult result){
		  
		    ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	for(ItemId item : result.getIds()){
		  		if(!this.hasLabel(item)){
		  			selectedItems.add(item);
		  			scores.add(result.getScores()[i]);
		  		}
		  		i++;
		  	}
		  	IPEDResult r = new IPEDResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	
	  public IPEDResult filtrarSelecionados(IPEDResult result) throws Exception {
		  	
		  	ArrayList<ItemId> selectedItems = new ArrayList<ItemId>();
		  	ArrayList<Float> scores = new ArrayList<Float>();
		  	int i = 0;
		  	for(ItemId item : result.getIds()){
		  		if(map.get(item.getSourceId()).isSelected(item.getId())){
		  			selectedItems.add(item);
		  			scores.add(result.getScores()[i]);
		  		}
		  		i++;
		  	}
		  	IPEDResult r = new IPEDResult(selectedItems.toArray(new ItemId[0]),
		  			ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
		  	
			return r;
	  }
	  
	  public void loadState(){
		  for(Marcadores m : map.values())
			  m.loadState();
	  }
	  
	  public void loadState(File file) throws ClassNotFoundException, IOException{
		  MultiMarcadores state = (MultiMarcadores) Util.readObject(file.getAbsolutePath());
		  this.map = state.map;
	  }
	  
	  public void saveState(){
		  for(Marcadores m : map.values())
			  m.saveState();
	  }
	  
	  public void saveState(File file) throws IOException{
		  Util.writeObject(this, file.getAbsolutePath());
	  }
	  
	  public LinkedHashSet<String> getTypedWords(){
		  LinkedHashSet<String> searches = new LinkedHashSet<String>(); 
		  for(Marcadores m : map.values())
			  for(String s : m.getTypedWords())
				if(!searches.contains(s))
					searches.add(s);
		  return searches;
		}
	  
	  public void clearTypedWords(){
		  for(Marcadores m : map.values())
			  m.getTypedWords().clear();
	  }
	  
	  public void addToTypedWords(String texto) {
		  for(Marcadores m : map.values())
			  m.addToTypedWords(texto);
	   }

}
