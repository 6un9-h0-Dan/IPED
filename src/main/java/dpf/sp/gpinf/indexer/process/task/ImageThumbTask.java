package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.search.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Util;

public class ImageThumbTask extends AbstractTask{
    
    public ImageThumbTask(Worker worker) {
		super(worker);
	}

	public static String thumbsFolder = "thumbs";
	
	private static String enableProperty = "enableImageThumbs";
	
    public static String HAS_THUMB = "hasThumb";
    
    private static int thumbSize = 160;
    
    private boolean taskEnabled = false;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        taskEnabled = Boolean.valueOf(confParams.getProperty(enableProperty));
        thumbSize = Integer.valueOf(confParams.getProperty("imgThumbSize"));
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        if(!taskEnabled || !isImageType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null)
            return;
        
        File thumbFile = Util.getFileFromHash(new File(output, thumbsFolder), evidence.getHash(), "jpg");
        if (!thumbFile.getParentFile().exists())
            thumbFile.getParentFile().mkdirs();

        //Já está na pasta? Então não é necessário gerar.
        if (thumbFile.exists()){
        	if(thumbFile.length() != 0)
        		evidence.setExtraAttribute(HAS_THUMB, true);
        	else
        		evidence.setExtraAttribute(HAS_THUMB, false);
            return;
        }
        
        createImageThumb(evidence, thumbFile);
        
    }
    
    /**
     * Verifica se é imagem.
     */
    public static boolean isImageType(MediaType mediaType) {
        return  mediaType.getType().equals("image") || 
                mediaType.toString().endsWith("msmetafile") || 
                mediaType.toString().endsWith("x-emf");
    }
    
    private void createImageThumb(EvidenceFile evidence, File thumbFile) {

    	File tmp = null;
        try {
            GalleryValue value = new GalleryValue(null, null, -1);
            BufferedImage img = null;
            if (evidence.getMediaType().getSubtype().startsWith("jpeg")) {
                BufferedInputStream stream = evidence.getBufferedStream();
                try{
                    img = ImageUtil.getThumb(stream, value);
                }finally{
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
                BufferedInputStream stream = evidence.getBufferedStream();
                try{
                    img = ImageUtil.getSubSampledImage(stream, thumbSize, thumbSize, value);
                }finally{
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
            	BufferedInputStream stream = evidence.getBufferedStream();
                try{
                    img = new GraphicsMagicConverter().getImage(stream, thumbSize);
                }finally{
                    IOUtil.closeQuietly(stream);
                }
            }
            
            tmp = File.createTempFile("iped", ".tmp", new File(output, thumbsFolder));
            
            if(img != null){
                if(img.getWidth() > thumbSize || img.getHeight() > thumbSize)
                	img = resizeThumb(img, thumbSize);
                img = ImageUtil.getOpaqueImage(img);
                
                ImageIO.write(img, "jpg", tmp);
            }
                
        } catch (Exception e) {
            e.printStackTrace();
            
        }finally{
        	if(tmp != null && !tmp.renameTo(thumbFile))
        		tmp.delete();
            
            if (thumbFile.exists() && thumbFile.length() != 0)
            	evidence.setExtraAttribute(HAS_THUMB, true);
            else
            	evidence.setExtraAttribute(HAS_THUMB, false);
        }
    }
    
    private static BufferedImage resizeThumb(BufferedImage img, int thumbSize) {
        int width = img.getWidth();
        int height = img.getHeight();
        if (width > height) {
            height = height * thumbSize / width;
            width = thumbSize;
        } else {
            width = width * thumbSize / height;
            height = thumbSize;
        }
        return ImageUtil.resizeImage(img, width, height);
    }

}
