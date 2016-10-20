package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.mg.udi.gpinf.shareazaparser.ShareazaLibraryDatParser;
import dpf.sp.gpinf.indexer.parsers.AresParser;
import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.SearchResult;
import dpf.sp.gpinf.indexer.util.HashValue;

public class P2PBookmarker {
	
	private static Logger LOGGER = LoggerFactory.getLogger(P2PBookmarker.class);
	
	class P2PProgram{
		List<HashValue> sharedHashes;
		String hashName;
		String appName;
		
		public P2PProgram(List<HashValue> sharedHashes, String hashName, String appName){
			this.sharedHashes = sharedHashes;
			this.hashName = hashName;
			this.appName = appName;
		}
	}

	public void createBookmarksForSharedFiles(File caseDir) {
		
		LOGGER.info("Pesquisando itens compartilhados via P2P...");

		ArrayList<P2PProgram> p2pPrograms = new ArrayList<P2PProgram>();
		
		p2pPrograms.add(new P2PProgram(KnownMetParser.getSharedHashes(), HashTask.EDONKEY, "Emule"));
		p2pPrograms.add(new P2PProgram(AresParser.getSharedHashes(), "sha-1", "Ares"));
		p2pPrograms.add(new P2PProgram(ShareazaLibraryDatParser.getSharedHashes(), "md5", "Shareaza"));
		
		for(P2PProgram program : p2pPrograms){
			List<HashValue> hashes = program.sharedHashes;
			if(hashes.size() == 0)
				continue;
			StringBuilder queryText = new StringBuilder();
			queryText.append(program.hashName + ":(");
			for (HashValue hash : hashes)
				queryText.append(hash.toString() + " ");
			queryText.append(")");
			IPEDSource ipedSrc = new IPEDSource(caseDir);
			IPEDSearcher searcher = new IPEDSearcher(ipedSrc, queryText.toString());
			queryText = null;
			try {
				SearchResult result = searcher.search();
				int labelId = ipedSrc.getMarcadores().newLabel("Compartilhados via " + program.appName);
				ArrayList<Integer> ids = new ArrayList<Integer>();
				for (int i = 0; i < result.getLength(); i++)
					ids.add(result.getId(i));
				ipedSrc.getMarcadores().addLabel(ids, labelId);
				ipedSrc.getMarcadores().saveState();
				
				LOGGER.info("Itens compartilhados via " + program.appName + " encontrados e marcados: " + result.getLength());

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}

}
