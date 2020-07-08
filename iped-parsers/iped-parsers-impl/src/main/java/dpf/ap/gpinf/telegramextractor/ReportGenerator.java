package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import com.drew.metadata.Tag;


import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

public class ReportGenerator {
	private final long MAXLEN=5000000;
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss XXX"); //$NON-NLS-1$
    private IItemSearcher searcher;
    public void setSearcher(IItemSearcher s) {
    	searcher=s;
    }
    
	public byte[] generateChatHtml(Chat c)
            throws UnsupportedEncodingException {
        

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        printMessageFileHeader(out, c.getName(), c.getId()+"");
        

        int currentMsg=0;
        String lastDate=null;
        while (currentMsg < c.getMessages().size()) {
            Message m = c.getMessages().get(currentMsg++);
            String thisDate = dateFormat.format(m.getTimeStamp());
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }
            
            printMessage(out, m, c.isGroup());
            if(bout.size()>MAXLEN) {
            	break;
            }
            
        }

        printMessageFileFooter(out);
        out.flush();

        return bout.toByteArray();
    }
	
	
	
	private void printVideo(PrintWriter out, Message message) {
		if(message.getMediaHash()!=null) {
			
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclik","app.open(\"sha-256:" + message.getMediaHash() + "\")");
			link.setAtribute("href", message.getMediaFile());
			
			byte thumb[] = message.getThumb();
            System.out.println("chegou aqui 1!!");
			if (thumb == null) {
            	List<IItemBase> result = null;
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("sha-256:"+ message.getMediaHash(),searcher);
            	if(result != null && !result.isEmpty()) {
            		
            		thumb = result.get(0).getThumb();
            	}
            	
            	
            }
			System.out.println("chegou aqui 2!!");
            
            TagHtml img=new TagHtml("img");
        	img.setAtribute("class", "iped-show");
        	if(thumb!=null) {
        		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
        	}
        	img.setAtribute("width", "100");
        	img.setAtribute("height", "102");
        	img.setAtribute("title","Video");
        	link.getInner().add(img);
        	link.getInner().add(" teste ");
        	System.out.println("chegou aqui 3!!");
        	System.out.println(link.toString());
        	out.println(link.toString());
        	out.println("<br/>");
			
        	TagHtml video=new TagHtml("video");
			video.setAtribute("class", "iped-hide");
			video.setAtribute("controls", null);
        	video.getInner().add("<source src=\"" + message.getMediaFile() + "\"/>");
			out.println(video.toString());
			out.println("<br/>");
			
			
			
		}else {
			 out.print("<img class=\"iped-show\" src=\"");
			 out.print(dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/video.png"));
             out.println("\" width=\"100\" height=\"102\" title=\"Video\"/>"); 
			
		}
		
	}
	
	
	private void printAudio(PrintWriter out, Message message) {
		TagHtml img=new TagHtml("img");
		
    	
    	img.setAtribute("src", dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/audio.png"));
    	
    	img.setAtribute("width", "100");
    	img.setAtribute("height", "102");
    	img.setAtribute("title","Video");
    	
		if(message.getMediaHash()!=null) {
			
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclik","app.open(\"sha-256:" + message.getMediaHash() + "\")");
			link.setAtribute("href", message.getMediaFile());
			           
			img.setAtribute("class", "iped-show");
        	link.getInner().add(img);
        	link.getInner().add(" teste ");
        	System.out.println("chegou aqui 3!!");
        	System.out.println(link.toString());
        	out.println(link.toString());
        	out.println("<br/>");
			
        	TagHtml audio=new TagHtml("audio");
        	audio.setAtribute("class", "iped-hide");
			audio.setAtribute("controls", null);
			audio.getInner().add("<source src=\"" + message.getMediaFile() + "\"/>");
			out.println(audio.toString());
			
			
			
			
		}else {
			
			out.println(img.toString());
		}
		
	}
	
	private void printImage(PrintWriter out, Message message) {
		if(message.getMediaHash()!=null) {
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclik","app.open(\"sha-256:" + message.getMediaHash() + "\")");
			link.setAtribute("href", message.getMediaFile());
			
			byte thumb[] = message.getThumb();
	      
			if (thumb == null) {
	        	List<IItemBase> result = null;
	        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("sha-256:"+ message.getMediaHash(),searcher);
	        	if(result != null && !result.isEmpty()) {
	        		
	        		thumb = result.get(0).getThumb();
	        	}
	        	
	        	
	        }
			
	        
	        TagHtml img=new TagHtml("img");
	    	img.setAtribute("class", "iped-show");
	    	if(thumb!=null) {
	    		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
	    	}
	    	img.setAtribute("width", "100");
	    	img.setAtribute("height", "102");
	    	img.setAtribute("title","Image");
	    	link.getInner().add(img);
	    	link.getInner().add(" teste ");
	    	
	    	System.out.println(link.toString());
	    	out.println(link.toString());
	    			
		}else {
			 out.print("<img src=\"");
			 out.print(dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/image.png"));
             out.println("\" width=\"100\" height=\"102\" title=\"Video\"/>"); 
		}
		out.println("<br/>");
		
	}
	
	private void printLink(PrintWriter out, Message message) {
		if(message.getLinkImage()!=null) {
					
			byte thumb[] = message.getLinkImage();
	      
				        
	        TagHtml img=new TagHtml("img");
	    	
	    	if(thumb!=null) {
	    		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
	    	}
	    	img.setAtribute("width", "100");
	    	img.setAtribute("height", "102");
	    	img.setAtribute("title","Link image");
	    		    	    	
	    	System.out.println("imagem de link");
	    	out.println(img);
	    	out.println("<br/>");
	    			
		}
		
		
	}
	
	
	private void printMessage(PrintWriter out, Message message, boolean group) {
		out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$
		if (message.isFromMe()) {
            out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
        } else {
            out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
            if (group) {
                
                
                String number = "";
                Contact contact = message.getRemetente();
                String name = contact == null ? null : contact.getName();
                if (name == null)
                    name = number;
                else
                    name += " (" + number + ")"; //$NON-NLS-1$ //$NON-NLS-2$
                out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" //$NON-NLS-1$
                        + name + "</span><br/>"); //$NON-NLS-1$
                
            }
        }
		if(message.getMediaMime()!=null) {
			if(message.getMediaMime().toLowerCase().startsWith("video")) {
				printVideo(out, message);
			}
			if(message.getMediaMime().toLowerCase().startsWith("image")) {
				printImage(out, message);
				
			}
			if(message.getMediaMime().toLowerCase().startsWith("audio")) {
				printAudio(out,message);
			}
			if(message.getMediaMime().toLowerCase().startsWith("link")) {
				printLink(out,message);
			}
			
		}
		if (message.getData() != null) {
            out.print(message.getData() + "<br/>"); //$NON-NLS-1$
        }
		
		out.println("<span class=\"time\">"); //$NON-NLS-1$
        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$ 
        out.println("</span>"); //$NON-NLS-1$
        
        out.println("</div></div>"); //$NON-NLS-1$
		
	}
	
	
	private static void printMessageFileHeader(PrintWriter out, String title, String id) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + id + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + "	<link rel=\"shortcut icon\" href=\"" + dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/favicon.ico")+ "\" />\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "<style>\n"
                + dpf.mg.udi.gpinf.whatsappextractor.Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-1$
                + "\n</style>\n"
                + "<script>\n" //$NON-NLS-1$
                + "var css = document.createElement(\"style\");\n" //$NON-NLS-1$
                + "css.type = \"text/css\";\n" //$NON-NLS-1$
                + "var inHtml = \"\";\n" //$NON-NLS-1$
                + "if (navigator.userAgent.search(\"JavaFX\") >= 0) {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: none; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: block; }\";\n" //$NON-NLS-1$
                + "} else {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: block; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: none; }\";\n" //$NON-NLS-1$
                + "}\n" //$NON-NLS-1$
                + "css.innerHTML = inHtml;\n" //$NON-NLS-1$
                + "document.head.appendChild(css);\n" //$NON-NLS-1$
                + "function openIfExists(url1, url2){\r\n" + "    var img1 = new Image();\r\n"
                + "    img1.onload = () => window.location = url1;\r\n"
                + "    img1.onerror = () => window.location = url2;\r\n" + "    img1.src = url1;\r\n" + "}\r\n"
                + "</script>\n" //$NON-NLS-1$
                + dpf.mg.udi.gpinf.vcardparser.VCardParser.HTML_STYLE + "</head>\n" //$NON-NLS-1$
                + "<style>.check {vertical-align: top;}</style>"
                + "<body>\n" //$NON-NLS-1$
                + "<div id=\"topbar\">\n" //$NON-NLS-1$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$
        
        out.println(title + "</span>\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"conversation\">\n" //$NON-NLS-1$
                + "<br/><br/><br/>"); //$NON-NLS-1$
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"lastmsg\">&nbsp;</div>\n" //$NON-NLS-1$
                + "</body>\n" //$NON-NLS-1$
                + "</html>"); //$NON-NLS-1$
    }

}
