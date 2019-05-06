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
package dpf.sp.gpinf.indexer.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.sax.TextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser customizado para arquivos XML. Usa um parser menos restrito que o parser 
 * padrão do Tika e tb executa o parser de strings para indexar elementos e atributos.
 * 
 * @author Nassif
 *
 */
public class XMLParser extends AbstractParser {

	private static final long serialVersionUID = 1L;
	private org.apache.tika.parser.xml.XMLParser xmlParser = new org.apache.tika.parser.xml.XMLParser();
	private HtmlParser htmlParser = new HtmlParser();
	private RawStringParser rawParser = new RawStringParser();

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return xmlParser.getSupportedTypes(context);
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

		TemporaryResources tmp = new TemporaryResources();
		try {
			TikaInputStream tis = TikaInputStream.get(stream, tmp);
			File file = tis.getFile();

			context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
			TextContentHandler textHandler = new TextContentHandler(handler, true);
			htmlParser.parse(tis, textHandler, metadata, context);
			
			tis = TikaInputStream.get(file);
			try {
				rawParser.parse(tis, handler, metadata, context);
			} finally {
				tis.close();
			}

		} finally {
			tmp.dispose();
		}

	}

}
