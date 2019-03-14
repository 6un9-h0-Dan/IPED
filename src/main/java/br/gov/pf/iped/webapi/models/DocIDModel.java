package br.gov.pf.iped.webapi.models;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** DocIDModel identifies a single document:
 * {
 *   "source": 0,
 *   "id": 0
 * }
*/
@ApiModel(value="Document")
public class DocIDModel{
	private int source;
	private int id;
	
	public DocIDModel() {
	}
	
	public DocIDModel(int source, int id) {
		this.source = source;
		this.id = id;
	}
	
	@ApiModelProperty()
	public int getSource(){
		return this.source;
	}

	public void setSource(int source) {
		this.source = source;
	}

	@ApiModelProperty()
	public int getId(){
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}
}   	

