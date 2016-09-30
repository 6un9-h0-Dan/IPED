package dpf.sp.gpinf.indexer.desktop;

import java.awt.Desktop;
import java.io.File;

import gpinf.dev.data.EvidenceFile;

public class ExternalFileOpen {
	
	public static void open(final int luceneId){
		new Thread() {
	        public void run() {
	          File file = null;
	          try {
	        	  EvidenceFile item = App.get().appCase.getItemByLuceneID(luceneId);
	              file = item.getTempFile();
	              file.setReadOnly();

	            if (file != null) {
	              Desktop.getDesktop().open(file.getCanonicalFile());
	            }

	          } catch (Exception e) {
	            // e.printStackTrace();
	            try {
	              if (System.getProperty("os.name").startsWith("Windows")) {
	                Runtime.getRuntime().exec(new String[]{"rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\""});
	              } else {
	                Runtime.getRuntime().exec(new String[]{"xdg-open", file.toURI().toURL().toString()});
	              }

	            } catch (Exception e2) {
	              e2.printStackTrace();
	              CopiarArquivos.salvarArquivo(luceneId);
	            }
	          }
	        }
	      }.start();
	}

}
