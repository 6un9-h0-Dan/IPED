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

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.EvidenceFileType;

import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.tika.mime.MediaType;

/*
 * Cria um org.apache.lucene.document.Document a partir das propriedades do itens, para ser adicionado ao índice.
 */
public class IndexItem {
	
	public static final String ID = "id";
	public static final String FTKID = "ftkId";
	public static final String PARENTID = "parentId";
	public static final String PARENTIDs = "parentIds";
	public static final String SLEUTHID = "sleuthId";
	public static final String PARENTSLEUTHID = "parentSleuthId";
	public static final String NAME = "nome";
	public static final String TYPE = "tipo";
	public static final String LENGTH = "tamanho";
	public static final String CREATED = "criacao";
	public static final String ACCESSED = "acesso";
	public static final String MODIFIED = "modificacao";
	public static final String PATH = "caminho";
	public static final String EXPORT = "export";
	public static final String CATEGORY = "categoria";
	public static final String HASH = "hash";
	public static final String ISDIR = "isDir";
	public static final String ISROOT = "isRoot";
	public static final String DELETED = "deletado";
	public static final String HASCHILD = "hasChildren";
	public static final String CARVED = "carved";
	public static final String OFFSET = "offset";
	public static final String PRIMARY = "primary";
	public static final String TIMEOUT = "timeout";
	public static final String CONTENTTYPE = "contentType";
	public static final String CONTENT = "conteudo";
	

	private static FieldType contentField = new FieldType();
	private static FieldType storedTokenizedNoNormsField = new FieldType();
	
	static {
		contentField.setIndexed(true);
		contentField.setOmitNorms(true);
		storedTokenizedNoNormsField.setIndexed(true);
		storedTokenizedNoNormsField.setOmitNorms(true);
		storedTokenizedNoNormsField.setStored(true);
	}
	
	public static Document Document(EvidenceFile evidence, Reader reader, SimpleDateFormat df) {
		Document doc = new Document();

		String value = String.valueOf(evidence.getId());
		doc.add(new StringField(ID, value, Field.Store.YES));

		value = evidence.getFtkID();
		if (value != null)
			doc.add(new StringField(FTKID, value, Field.Store.YES));

		value = evidence.getSleuthId();
		if (value != null)
			doc.add(new StringField(SLEUTHID, value, Field.Store.YES));

		value = evidence.getParentSleuthId();
		if (value != null)
			doc.add(new StringField(PARENTSLEUTHID, value, Field.Store.YES));
		
		value = evidence.getParentId();
		if (value == null)
			if (evidence.getEmailPai() != null)
				value = String.valueOf(evidence.getEmailPai().getId());
		if (value != null)
			doc.add(new StringField(PARENTID, value, Field.Store.YES));
		
		doc.add(new Field(PARENTIDs, evidence.getParentIdsString(), storedTokenizedNoNormsField));

		
		value = evidence.getName();
		if (value == null)
			value = "";
		Field nameField = new TextField(NAME, value, Field.Store.YES);
		nameField.setBoost(1000.0f);
		doc.add(nameField);

		
		EvidenceFileType fileType = evidence.getType();
		if (fileType != null)
			value = fileType.getLongDescr();
		else
			value = "";
		doc.add(new Field(TYPE, value, storedTokenizedNoNormsField));

		
		Long length = evidence.getLength();
		if (length != null)
			value = length.toString();
		else
			value = "";
		doc.add(new StoredField(LENGTH, value));

		/*
		 * long length = -1; if(evidence.getLength() != null) length =
		 * evidence.getLength(); LongField longfield = new LongField("tamanho",
		 * length, Field.Store.YES); doc.add(longfield);
		 * 
		 * if(evidence.getCreationDate() != null) value =
		 * DateTools.dateToString(evidence.getCreationDate(),
		 * DateTools.Resolution.SECOND); else value = ""; doc.add(new
		 * Field("criacao", value, Field.Store.YES, Field.Index.NOT_ANALYZED));
		 */

		Date date = evidence.getCreationDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField(CREATED, value));

		date = evidence.getAccessDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField(ACCESSED, value));

		date = evidence.getModDate();
		if (date != null)
			value = df.format(date);
		else
			value = "";
		doc.add(new StoredField(MODIFIED, value));

		value = evidence.getPath();
		if (value == null)
			value = "";
		doc.add(new Field(PATH, value, storedTokenizedNoNormsField));

		doc.add(new Field(EXPORT, evidence.getFileToIndex(), storedTokenizedNoNormsField));
		
		doc.add(new Field(CATEGORY, evidence.getCategories(), storedTokenizedNoNormsField));

		MediaType type = evidence.getMediaType();
		if (type != null)
			value = type.toString();
		else
			value = "";
		doc.add(new Field(CONTENTTYPE, value, storedTokenizedNoNormsField));

		doc.add(new StoredField(TIMEOUT, Boolean.toString(evidence.timeOut)));

		value = evidence.getHash();
		if (value != null)
			doc.add(new StringField(HASH, value.toLowerCase(), Field.Store.YES));

		value = Boolean.toString(evidence.isPrimaryHash());
		doc.add(new StringField(PRIMARY, value, Field.Store.YES));

		value = Boolean.toString(evidence.isDeleted());
		doc.add(new StringField(DELETED, value, Field.Store.YES));
		
		value = Boolean.toString(evidence.hasChildren());
		doc.add(new StringField(HASCHILD, value, Field.Store.YES));
		
		value = Boolean.toString(evidence.isDir());
		doc.add(new StringField(ISDIR, value, Field.Store.YES));
		
		if(evidence.isRoot())
			doc.add(new StringField(ISROOT, "true", Field.Store.YES));
		
		value = Boolean.toString(evidence.isCarved());
		doc.add(new StringField(CARVED, value, Field.Store.YES));
		
		long off = evidence.getFileOffset();
		if(off != -1)
			doc.add(new StoredField(OFFSET, Long.toString(off)));

		if (reader != null)
			doc.add(new Field(CONTENT, reader, contentField));

		return doc;
	}

}
