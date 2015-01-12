package dpf.sp.gpinf.indexer.process.task;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;
import gpinf.dev.filetypes.UnknownFileType;

public class SetTypeTask extends AbstractTask {
	
	TikaConfig config;
	
	public SetTypeTask(Worker worker){
		super(worker);
		config = worker.config;
	}

	@Override
	public void process(EvidenceFile evidence) throws Exception {
		
		if (evidence.getType() == null) {
			String ext = getExtBySig(evidence);
			if (!ext.isEmpty()){
				if(ext.length() > 1 && evidence.isCarved() && evidence.getName().startsWith("Carved-")){
					evidence.setName(evidence.getName() + ext);
					evidence.setPath(evidence.getPath() + ext);
				}
				ext = ext.substring(1);
			}
			evidence.setType(new UnknownFileType(ext));
		}

	}
	
	public String getExtBySig(EvidenceFile evidence) {

		String ext = "";
		String ext1 = "." + evidence.getExt();
		MediaType mediaType = evidence.getMediaType();
		if (!mediaType.equals(MediaType.OCTET_STREAM))
			try {
				do {
					boolean first = true;
					for (String ext2 : config.getMimeRepository().forName(mediaType.toString()).getExtensions()) {
						if (first) {
							ext = ext2;
							first = false;
						}
						if (ext2.equals(ext1)) {
							ext = ext1;
							break;
						}
					}

				} while (ext.isEmpty() && !MediaType.OCTET_STREAM.equals((mediaType = config.getMediaTypeRegistry().getSupertype(mediaType))));
			} catch (MimeTypeException e) {
			}

		if (ext.isEmpty())
			ext = ext1;

		return ext;

	}

}
