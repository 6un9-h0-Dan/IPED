package dpf.inc.sepinf.browsers.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para histórico do Safari - plist
 *
 * https://medium.com/@karaiskc/understanding-apples-binary-property-list-format-281e6da00dbd
 * https://github.com/3breadt/dd-plist
 * 
 * @author Paulo César Herrmann Wanner <herrmann.pchw@dpf.gov.br>
 */
public class SafariPlistParser  extends AbstractParser {
	
public static final MediaType SAFARI_PLIST = MediaType.application("x-safari-plist"); //$NON-NLS-1$
	
	public static final MediaType SAFARI_HISTORY = MediaType.application("x-safari-history"); //$NON-NLS-1$
	
	public static final MediaType SAFARI_HISTORY_REG = MediaType.application("x-safari-history-registry"); //$NON-NLS-1$
	
	public static final MediaType SAFARI_DOWNLOADS = MediaType.application("x-safari-downloads"); //$NON-NLS-1$
	
	public static final MediaType SAFARI_DOWNLOADS_REG = MediaType.application("x-safari-downloads-registry"); //$NON-NLS-1$
	
	private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(SAFARI_PLIST);

	private static Logger LOGGER = LoggerFactory.getLogger(SafariPlistParser.class);

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
			throws IOException, SAXException, TikaException {
	
		TemporaryResources tmp = new TemporaryResources();
		File historyFile = tmp.createTemporaryFile();
		File downloadFile = tmp.createTemporaryFile();
		File bookmarkFile = tmp.createTemporaryFile();

		try {
			EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
			
			TikaInputStream tis = TikaInputStream.get(stream, tmp);
            String evidenceFile = ((ItemInfo) context.get(ItemInfo.class)).getPath();
						
			if (extractor.shouldParseEmbedded(metadata)) {
				
				/* History.plist */
				if (evidenceFile.contains("History")) {
					
					List<SafariResumedVisit> resumedHistory = getResumedHistory(stream);
//					List<SafariVisit> history = getHistory(stream);
					
					try (FileOutputStream tmpHistoryFile = new FileOutputStream(historyFile)) {
						
						ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
						Metadata historyMetadata = new Metadata();
						historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_HISTORY.toString());
						historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist History"); //$NON-NLS-1$
						historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
						historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
						
						parseSafariResumedHistory(stream, historyHandler, historyMetadata, context, resumedHistory);
						
						try (FileInputStream fis = new FileInputStream(historyFile)) {
							extractor.parseEmbedded(fis, handler, historyMetadata, true);
						}
					}
					
					int i = 0;
					
					for (SafariResumedVisit v : resumedHistory) {
						
						i++;
						Metadata metadataHistory = new Metadata();
						
						metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_HISTORY_REG.toString()); 
						metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist History Entry " + i); //$NON-NLS-1$
						metadataHistory.add(TikaCoreProperties.TITLE, v.getTitle());
						metadataHistory.set(TikaCoreProperties.CREATED, v.getLastVisitDate());
						metadataHistory.add(TikaCoreProperties.IDENTIFIER, v.getUrl());
						metadataHistory.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));
						
						extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistory, true);
					}
				}
				
				/* Downloads.plist */
				if (evidenceFile.contains("Downloads")) {
					
					List<SafariDownload> downloads = getDownloads(stream);
					
					try (FileOutputStream tmpDownloadFile = new FileOutputStream(downloadFile)) {
						
						ToXMLContentHandler downloadHandler = new ToXMLContentHandler(tmpDownloadFile, "UTF-8"); //$NON-NLS-1$
						Metadata downloadMetadata = new Metadata();
						downloadMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_DOWNLOADS.toString());
						downloadMetadata.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist Downloads"); //$NON-NLS-1$
						downloadMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
						downloadMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
						
						parseSafariDownloads(stream, downloadHandler, downloadMetadata, context, downloads);
						
						try (FileInputStream fis = new FileInputStream(downloadFile)) {
							extractor.parseEmbedded(fis, handler, downloadMetadata, true);
						}
					}
					
					int i = 0;
					
					for (SafariDownload d : downloads) {
						
						i++;
						Metadata metadataDownload = new Metadata();
						
						metadataDownload.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_DOWNLOADS_REG.toString()); 
						metadataDownload.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist Download Entry " + i); //$NON-NLS-1$
						metadataDownload.add(TikaCoreProperties.IDENTIFIER, d.getUrlFromDownload());
						metadataDownload.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, d.getDownloadedLocalPath());
						metadataDownload.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));
						
						extractor.parseEmbedded(new EmptyInputStream(), handler, metadataDownload, true);
					}
				}
				
				/* Bookmarks.plist */
				if (evidenceFile.contains("Bookmarks")) {
					
					List<SafariBookmark> bookmarks = getBookmarks(stream);
					
					try (FileOutputStream tmpBookmarkFile = new FileOutputStream(bookmarkFile)) {
						
						ToXMLContentHandler bookmarkHandler = new ToXMLContentHandler(tmpBookmarkFile, "UTF-8"); //$NON-NLS-1$
						Metadata bookmarkMetadata = new Metadata();
						bookmarkMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_DOWNLOADS.toString());
						bookmarkMetadata.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist Bookmarks"); //$NON-NLS-1$
						bookmarkMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(2));
						bookmarkMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
						
						parseSafariBookmarks(stream, bookmarkHandler, bookmarkMetadata, context, bookmarks);
						
						try (FileInputStream fis = new FileInputStream(bookmarkFile)) {
							extractor.parseEmbedded(fis, handler, bookmarkMetadata, true);
						}
					}
					
					int i = 0;
					
					for (SafariBookmark b : bookmarks) {
						
						i++;
						Metadata metadataBookmark = new Metadata();
						
						metadataBookmark.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_DOWNLOADS_REG.toString()); 
						metadataBookmark.add(Metadata.RESOURCE_NAME_KEY, "Safari Plist Bookmark Entry " + i); //$NON-NLS-1$
						metadataBookmark.add(TikaCoreProperties.TITLE, b.getTitle());
						metadataBookmark.add(TikaCoreProperties.IDENTIFIER, b.getUrl());
						metadataBookmark.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(2));
						
						extractor.parseEmbedded(new EmptyInputStream(), handler, metadataBookmark, true);
					}
				}
			}
		} catch (Exception e) {
			throw new TikaException("Plist parsing exception", e); //$NON-NLS-1$
		} finally {
			tmp.close();
		}
		
	}

	private void parseSafariResumedHistory(InputStream stream, ContentHandler handler, Metadata metadata,
			ParseContext context, List<SafariResumedVisit> resumedHistory) 
					throws IOException, SAXException, TikaException {
		
		XHTMLContentHandler xHandler = null;

        try {
        	
            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();
            
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Safari Visited Sites Plist History"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            
            xHandler.startElement("table"); //$NON-NLS-1$
            
            xHandler.startElement("tr"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("TITLE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("VISIT COUNT"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("LAST VISIT DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$
        	
            int i = 1;
            
            for (SafariResumedVisit h : resumedHistory) {
            	
        		xHandler.startElement("tr"); //$NON-NLS-1$
            	
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getTitle());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Long.toString(h.getVisitCount()));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getLastVisitDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getUrl());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$           
            
            xHandler.endDocument();            
            
        } finally{
        	if(xHandler != null)
        		xHandler.endDocument();
        }
	}
        
    protected List<SafariResumedVisit> getResumedHistory(InputStream is) throws Exception {
		List<SafariResumedVisit> resumedHistory = new LinkedList<SafariResumedVisit>();
		
		NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
		NSObject[] parameters = ((NSArray) rootDict.objectForKey("WebHistoryDates")).getArray();
		long i = 0;
		for (NSObject param : parameters) {
			i++;
			NSDictionary d = (NSDictionary) param;
			//String uid = ((NSString) d.objectForKey("DownloadEntryIdentifier")).getContent();
			String url = ((NSString) d.objectForKey("")).getContent();
			NSString t = (NSString) d.objectForKey("title");
			String title = "";
			if (t != null) title = t.getContent();
			long visitCount = ((NSNumber) d.objectForKey("visitCount")).longValue();
			Double lastVisitedDate = ((Double.parseDouble(((NSString) d.objectForKey("lastVisitedDate")).getContent()) + 978307200) * 1000);
			//int totalBytes = ((NSNumber) d.objectForKey("DownloadEntryProgressTotalToLoad")).intValue();
			//int bytesDownloaded = ((NSNumber) d.objectForKey("DownloadEntryProgressBytesSoFar")).intValue();

			resumedHistory.add(new SafariResumedVisit(i, title, url, visitCount, lastVisitedDate.longValue()));
		}
	
		return resumedHistory;
	}
    
//    protected List<SafariVisit> getHistory(InputStream is) throws Exception {
//		List<SafariVisit> history = new LinkedList<SafariVisit>();
//		
//		NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
//		NSObject[] parameters = ((NSArray) rootDict.objectForKey("WebHistoryDates")).getArray();
//		long i = 0;
//		for (NSObject param : parameters) {
//			i++;
//			NSDictionary d = (NSDictionary) param;
//			//String uid = ((NSString) d.objectForKey("DownloadEntryIdentifier")).getContent();
//			String url = ((NSString) d.objectForKey("DownloadEntryURL")).getContent();
//			String title = ((NSString) d.objectForKey("DownloadEntryPath")).getContent();
//			//String visitCount = ((NSString) d.objectForKey("visitCount")).getContent();
//			long lastVisitedDate = Long.parseLong(((NSString) d.objectForKey("lastVisitedDate")).getContent());
//			//int totalBytes = ((NSNumber) d.objectForKey("DownloadEntryProgressTotalToLoad")).intValue();
//			//int bytesDownloaded = ((NSNumber) d.objectForKey("DownloadEntryProgressBytesSoFar")).intValue();
//			
//			history.add(new SafariVisit(i, title, lastVisitedDate, url));
//			
//		}
//		
//		return history;
//	}
    

	private List<SafariDownload> getDownloads(InputStream is) throws Exception {
		List<SafariDownload> downloads = new LinkedList<SafariDownload>();
		
		NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
		NSObject[] parameters = ((NSArray) rootDict.objectForKey("DownloadHistory")).getArray();
		for (NSObject param : parameters) {
			NSDictionary d = (NSDictionary) param;
			String uid = ((NSString) d.objectForKey("DownloadEntryIdentifier")).getContent();
			String url = ((NSString) d.objectForKey("DownloadEntryURL")).getContent();
			String path = ((NSString) d.objectForKey("DownloadEntryPath")).getContent();
			//int totalBytes = ((NSNumber) d.objectForKey("DownloadEntryProgressTotalToLoad")).intValue();
			//int bytesDownloaded = ((NSNumber) d.objectForKey("DownloadEntryProgressBytesSoFar")).intValue();
			
			downloads.add(new SafariDownload(uid, url, path));
			
		}
		return downloads;
	}
	
	private void parseSafariDownloads(InputStream stream, ToXMLContentHandler downloadHandler, Metadata downloadMetadata,
			ParseContext context, List<SafariDownload> downloads) throws SAXException {
		XHTMLContentHandler xHandler = null;

        try {
        	
            xHandler = new XHTMLContentHandler(downloadHandler, downloadMetadata);
            xHandler.startDocument();
            
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Safari Plist Downloaded Files"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            
            xHandler.startElement("table"); //$NON-NLS-1$
            
            xHandler.startElement("tr"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("DOWNLOADED FILE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$
            
            int i = 1;
            
            for (SafariDownload d : downloads) {
            	xHandler.startElement("tr"); //$NON-NLS-1$
            	
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getUrlFromDownload());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedLocalPath());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$           
            
            xHandler.endDocument();            
            
        } finally{
        	if(xHandler != null)
        		xHandler.endDocument();
        }
	}
	
	private void parseChildren(int i, List<SafariBookmark> bookmarks, NSObject[] children) {
		
//        System.out.println("=== Children ===");
        for (NSObject child : children) {
            i++;
            NSDictionary d = (NSDictionary) child;

            String bookmarkType = ((NSString) d.objectForKey("WebBookmarkType")).getContent();
            String uuid = ((NSString) d.objectForKey("WebBookmarkUUID")).getContent();
            String title = "";
            String identifier = "";

            if (bookmarkType.equalsIgnoreCase("WebBookmarkTypeProxy")) {
                title = ((NSString) d.objectForKey("Title")).getContent();
                identifier = ((NSString) d.objectForKey("WebBookmarkIdentifier")).getContent();
            }

//            System.out.println("Title: " + title);
//            System.out.println("Bookmark Type: " + bookmarkType);
//            System.out.println("Identifier: " + identifier);
//            System.out.println("UUID: " + uuid);

            if (bookmarkType.equalsIgnoreCase("WebBookmarkTypeList")) {
                NSArray c = (NSArray) d.objectForKey("Children");
                if (c != null) {
//                    System.out.println("Children:");
                    parseChildren(i, bookmarks, c.getArray());
                }

            } else if (bookmarkType.equalsIgnoreCase("WebBookmarkTypeLeaf")) {

                NSDictionary uriDictionary = ((NSDictionary) d.objectForKey("URIDictionary"));
                NSString urlTitle = (NSString) uriDictionary.objectForKey("title");
                String urlString = ((NSString) d.objectForKey("URLString")).getContent();
                String ut = "";

                if (urlTitle != null) {
                	ut = urlTitle.getContent();
//                    System.out.println("\t URL Title: " + ut);
                }
//                System.out.println("\t URL String: " + urlString);
                bookmarks.add(new SafariBookmark(i, uuid, ut, urlString));
                
            }
        }
    }
	
	private List<SafariBookmark> getBookmarks(InputStream is) throws Exception {
		List<SafariBookmark> bookmarks = new LinkedList<SafariBookmark>();
		
		NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
		String title = ((NSString) rootDict.objectForKey("Title")).getContent();
        String bookmarkType = ((NSString) rootDict.objectForKey("WebBookmarkType")).getContent();

//        System.out.println("Title: " + title);
//        System.out.println("Bookmark Type: " + bookmarkType);
        
        int i = 0;

        if (bookmarkType.equalsIgnoreCase("WebBookmarkTypeList")) {
            parseChildren(i, bookmarks, ((NSArray) rootDict.objectForKey("Children")).getArray());
        }

		return bookmarks;
	}
	
	private void parseSafariBookmarks(InputStream stream, ToXMLContentHandler bookmarkHandler, Metadata bookmarkMetadata,
			ParseContext context, List<SafariBookmark> bookmarks) throws SAXException {
		XHTMLContentHandler xHandler = null;

        try {
        	
            xHandler = new XHTMLContentHandler(bookmarkHandler, bookmarkMetadata);
            xHandler.startDocument();
            
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Safari Plist Bookmarks"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            
            xHandler.startElement("table"); //$NON-NLS-1$
            
            xHandler.startElement("tr"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("UUID"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("TITLE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$
            
            int i = 1;
            
            for (SafariBookmark b : bookmarks) {
            	xHandler.startElement("tr"); //$NON-NLS-1$
            	
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getUuid());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getTitle());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getUrl());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$           
            
            xHandler.endDocument();            
            
        } finally{
        	if(xHandler != null)
        		xHandler.endDocument();
        }
	}
}

