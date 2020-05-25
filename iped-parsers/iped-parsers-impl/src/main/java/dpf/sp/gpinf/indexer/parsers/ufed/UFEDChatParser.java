package dpf.sp.gpinf.indexer.parsers.ufed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mg.udi.gpinf.whatsappextractor.Message;
import dpf.sp.gpinf.indexer.parsers.util.IndentityHtmlParser;
import dpf.sp.gpinf.indexer.util.DateUtil;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;

public class UFEDChatParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType UFED_CHAT_MIME = MediaType.application("x-ufed-chat"); //$NON-NLS-1$
    public static final MediaType UFED_CHAT_WA_MIME = MediaType.application("x-ufed-chat-whatsapp"); //$NON-NLS-1$

    public static final String META_PHONE_OWNER = ExtraProperties.UFED_META_PREFIX + "phoneOwner"; //$NON-NLS-1$
    public static final String META_FROM_OWNER = ExtraProperties.UFED_META_PREFIX + "fromOwner"; //$NON-NLS-1$
    
    public static final String ATTACHED_MEDIA_MSG = "ATTACHED_MEDIA: ";

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(UFED_CHAT_MIME, UFED_CHAT_WA_MIME);

    public static void setSupportedTypes(Set<MediaType> supportedTypes) {
        SUPPORTED_TYPES = supportedTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            IItemBase chat = context.get(IItemBase.class);

            if (chat == null || searcher == null)
                return;

            String query = BasicProps.PARENTID + ":" + chat.getId(); //$NON-NLS-1$
            List<IItemBase> items = searcher.search(query);

            List<Message> messages = new ArrayList<>();

            for (IItemBase msg : items) {
                String META_PREFIX = ExtraProperties.UFED_META_PREFIX;
                Message m = new Message();
                m.setId(msg.getId());
                for(String body : msg.getMetadata().getValues(ExtraProperties.MESSAGE_BODY)) {
                    if(!body.startsWith(ATTACHED_MEDIA_MSG))
                        m.setData(body);
                }
                m.setFromMe(Boolean.valueOf(msg.getMetadata().get(META_FROM_OWNER)));
                String str = msg.getMetadata().get(ExtraProperties.MESSAGE_DATE);
                if (str != null) {
                    Date date = DateUtil.tryToParseDate(str);
                    m.setTimeStamp(date);
                }
                if (!m.isFromMe()) {
                    m.setRemoteResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_FROM));
                    m.setLocalResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_TO));
                } else {
                    m.setRemoteResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_TO));
                    m.setLocalResource(msg.getMetadata().get(org.apache.tika.metadata.Message.MESSAGE_FROM));
                }

                if (msg.getMediaType().equals(MediaTypes.UFED_MESSAGE_ATTACH_MIME)) {
                    query = msg.getMetadata().get(ExtraProperties.REFERENCED_FILE_QUERY);
                    List<IItemBase> attachs = searcher.search(query);
                    if (attachs.size() != 0) {
                        //TODO get extra attachments, must also update query above
                        IItemBase attach = attachs.get(0);
                        m.setMediaHash(attach.getHash(), false);
                        m.setMediaName(attach.getName());
                        m.setMediaUrl(attach.getMetadata().get(META_PREFIX + "URL")); //$NON-NLS-1$
                        m.setMediaCaption(attach.getMetadata().get(META_PREFIX + "Title")); //$NON-NLS-1$
                        m.setThumbData(attach.getThumb());
                        if (attach.isDeleted())
                            m.setDeleted(true);
                        if (attach.getLength() != null)
                            m.setMediaSize(attach.getLength());
                        if (attach.getMediaType() != null && !attach.getMediaType().equals(MediaType.OCTET_STREAM))
                            m.setMediaMime(attach.getMediaType().toString());
                        else
                            m.setMediaMime(attach.getMetadata().get(META_PREFIX + "ContentType")); //$NON-NLS-1$
                        if (attachs.size() > 1)
                            System.out.println("multiple_attachs: " //$NON-NLS-1$
                                    + msg.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "id")); //$NON-NLS-1$
                    }
                }

                messages.add(m);
            }

            Collections.sort(messages, new MessageComparator());
            
            Metadata chatMetadata = new Metadata();
            storeLinkedHashes(messages, chatMetadata);

            String chatName = getChatName(chat);
            chatMetadata.set(TikaCoreProperties.TITLE, chatName);

            ReportGenerator reportGenerator = new ReportGenerator(searcher);
            byte[] bytes = reportGenerator.generateFullChatHtml(chat, messages);
            if(bytes != null) {
                ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                new IndentityHtmlParser().parse(chatStream, context, xhtml);
            }
            

        } catch (Exception e) {
            e.printStackTrace();
            throw e;

        }finally {
            xhtml.endDocument();
        }

    }
    
    public static String getChatName(IItemBase item) {
        String name = "Chat"; //$NON-NLS-1$
        String source = item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + "Source"); //$NON-NLS-1$
        if (source != null)
            name += "_" + source; //$NON-NLS-1$
        String[] parties = item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + "Participants"); //$NON-NLS-1$
        if (parties != null && parties.length > 2) {
            name += "_Group_" + item.getName().split("_")[1]; //$NON-NLS-1$ //$NON-NLS-2$
        } else if (parties != null && parties.length > 0) {
            name += "_" + parties[0]; //$NON-NLS-1$
            if (parties.length > 1)
                name += "_" + parties[1]; //$NON-NLS-1$
        }
        return name;
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash());
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());
            }
        }
    }

    private class MessageComparator implements Comparator<Message> {

        @Override
        public int compare(Message o1, Message o2) {
            if (o1.getTimeStamp() == null) {
                if (o2.getTimeStamp() == null)
                    return 0;
                else
                    return -1;
            } else if (o2.getTimeStamp() == null)
                return 1;
            else
                return o1.getTimeStamp().compareTo(o2.getTimeStamp());
        }

    }

}
