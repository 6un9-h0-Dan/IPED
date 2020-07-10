package dpf.ap.gpinf.telegramextractor;

import java.util.Date;

public class Message {
	private long id;
	private String mediaHash=null;
	private String mediaFile=null;
	private String mediaName=null;
	boolean fromMe=false;
	private String type=null;
	private Contact remetente=null;
	private Chat chat=null;
	private String data=null;
	private Date timeStamp=null;
	private String mediaMime=null;
	private boolean link=false;
	private byte[] linkImage=null;
	private byte[] thumb=null;
	private String hashThumb=null;
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getMediaHash() {
		return mediaHash;
	}
	public void setMediaHash(String mediaHash) {
		this.mediaHash = mediaHash;
	}
	public String getMediaFile() {
		return mediaFile;
	}
	public void setMediaFile(String mediaFile) {
		this.mediaFile = mediaFile;
	}
	public boolean isFromMe() {
		return fromMe;
	}
	public void setFromMe(boolean fromMe) {
		this.fromMe = fromMe;
	}
	
	public Message(long id,Chat c) {
		this.id=id;
		setChat(c);
	}
	public Contact getRemetente() {
		return remetente;
	}
	public void setRemetente(Contact remetente) {
		this.remetente = remetente;
	}
	public Chat getChat() {
		return chat;
	}
	public void setChat(Chat chat) {
		this.chat = chat;
	}
	public String getData() {
		return data;
	}
	public void setData(String data) {
		this.data = data;
	}
	public Date getTimeStamp() {
		return timeStamp;
	}
	public void setTimeStamp(Date timeStamp) {
		this.timeStamp = timeStamp;
	}
	public String getMediaMime() {
		return mediaMime;
	}
	public void setMediaMime(String mediaMime) {
		this.mediaMime = mediaMime;
	}
	public boolean isLink() {
		return link;
	}
	public void setLink(boolean link) {
		this.link = link;
	}
	public byte[] getLinkImage() {
		return linkImage;
	}
	public void setLinkImage(byte[] linkImage) {
		this.linkImage = linkImage;
	}
	public byte[] getThumb() {
		return thumb;
	}
	public void setThumb(byte[] thumb) {
		this.thumb = thumb;
	}
	public String getHashThumb() {
		return hashThumb;
	}
	public void setHashThumb(String hashThumb) {
		this.hashThumb = hashThumb;
	}
	public String getMediaName() {
		return mediaName;
	}
	public void setMediaName(String mediaName) {
		this.mediaName = mediaName;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
}
