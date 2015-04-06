package gpinf.dev.data;

import gpinf.dev.filetypes.EvidenceFileType;
import gpinf.util.FormatUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.util.LimitedInputStream;
import dpf.sp.gpinf.indexer.util.StreamSource;

/**
 * Classe que define um arquivo de evidência, que é um arquivo do caso,
 * acompanhado de todas sua propriedades disponíveis. Algumas propriedades,
 * consideradas essenciais, tais como nome, tipo do arquivo e o link para onde
 * foi exportado, são consideradas atributos da classe. As outras "básicas" (que
 * já vem prontas para o pré-processamento) são guardades em uma lista de
 * propriedades.
 * 
 * @author Wladimir Leite (GPINF/SP)
 * @author Nassif (GPINF/SP)
 */
public class EvidenceFile implements Serializable, StreamSource {
    
    private static class Counter {

        public static synchronized int getNextId() {
            return nextId++;
        }

        public static int nextId = 0;

    }

    /**
     * @param start id inicial para itens adicionados após o processamento inicial
     */
    public static void setStartID(int start) {
        Counter.nextId = start;
    }

    /** Identificador utilizado para serialização da classe. */
    private static final long serialVersionUID = 98653214753695125L;

    /** Nome do arquivo. */
    private String name;

    /** Extensão arquivo. */
    private String extension;

    /**
     * Caminho completo do arquivo, armazenado em um nó de uma estrutura
     * navegável de arquivos e pastas do caso.
     */
    private PathNode path;

    private String pathString;

    /** Tipo do arquivo. */
    private EvidenceFileType type;

    private MediaType mediaType;

    private boolean deleted = false;

    private int id = -1;

    private String ftkID;

    private String parentId;

    private List<Integer> parentIds = new ArrayList<Integer>();

    private HashMap<String, Object> extraAttributes = new HashMap<String, Object>();

    /** Data de criação do arquivo. */
    private Date creationDate;

    /** Data da última alteração do arquivo. */
    private Date modificationDate;

    /** Data do último acesso do arquivo. */
    private Date accessDate;

    /** Tamanho do arquivo em bytes. */
    private Long length;

    /** Nome e caminho relativo que o arquivo foi exportado. */
    private String exportedFile;

    /**
     * Nome e caminho relativo do arquivo alternativo. Este arquivo é
     * utilizado por exemplo quando no próprio relatório há uma outra forma de
     * visualização do arquivo de evidência.
     */
    private String alternativeFile;

    /** Nome e caminho relativo que o arquivo para visualização. */
    private String viewFile;

    private EvidenceFile emailPai;

    private List<EvidenceFile> attachments = new ArrayList<EvidenceFile>();

    private HashSet<String> categories = new HashSet<String>();

    private String labels;

    private boolean timeOut = false;

    private boolean duplicate = false;

    private boolean isSubItem = false, hasChildren = false;

    private boolean isDir = false, isRoot = false;
    
    private boolean toIgnore = false, addToCase = true, isToExtract = false;

    private boolean carved = false, encrypted = false;

    private boolean isQueueEnd = false, parsed = false;

    private String parsedTextCache;

    private String hash;

    private File file, tmpFile;

    private TemporaryResources tmpResources = new TemporaryResources();

    private AbstractFile sleuthFile;

    private long startOffset = -1;

    private String sleuthId;

    private TikaInputStream tis;

    static final int BUF_LEN = 8 * 1024 * 1024;

    /**
     * Adiciona um anexo. Utilizado apenas em reports do FTK1.
     * 
     * @param attachment anexo do item/email atual
     */
    public void addAttachment(EvidenceFile attachment) {
        this.attachments.add(attachment);
    }

    /**
     * Adiciona o item a uma categoria.
     * 
     * @param category categoria a qual o item será adicionado
     */
    public void addCategory(String category) {
        this.categories.add(category);
    }

    /**
     * Adiciona o id de um dos pais do item numa estrutura hierárquica
     * 
     * @param parentId id de um dos pais
     */
    public void addParentId(int parentId) {
        this.parentIds.add(parentId);
    }

    /**
     * Adiciona uma lista de ids dos pais do item numa estrutura hierárquica
     * 
     * @param parentIds lista de ids dos pais do item
     */
    public void addParentIds(List<Integer> parentIds) {
        this.parentIds.addAll(parentIds);
    }

    @Override
    public EvidenceFile clone() {
        return this.clone();
    }

    /**
     * Libera recursos utilizados, como arquivos temporários e handles
     * 
     * @throws IOException caso ocorra erro de IO
     */
    public void dispose() throws IOException {
        tmpResources.close();
    }

    /**
     * @return data do último acesso
     */
    public Date getAccessDate() {
        return accessDate;
    }

    /**
     * @return nome e caminho do arquivo alternativo
     */
    public String getAlternativeFile() {
        return alternativeFile;
    }

    /**
     * @return lista de anexos do item. Funciona apenas com reports do FTK1
     */
    public List<EvidenceFile> getAttachments() {
        return this.attachments;
    }

    /**
     * @return um BufferedInputStream com o conteúdo do item
     * @throws IOException
     */
    public BufferedInputStream getBufferedStream() throws IOException {

        int len = 8192;
        if (length != null && length > len)
            if (length < BUF_LEN)
                len = length.intValue();
            else
                len = BUF_LEN;

        return new BufferedInputStream(getStream(), len);
    }

    /**
     *
     * @return o nome das categorias do item concatenadas
     */
    public String getCategories() {
        String names = "";
        int i = 0;
        for (String bookmark : categories) {
            names += bookmark;
            if (++i < categories.size())
                names += CategoryTokenizer.SEPARATOR;
        }
        return names;
    }

    /**
     * 
     * @return o conjunto de categorias do item
     */
    public HashSet<String> getCategorySet() {
        return categories;
    }

    /**
     * @return data de criação do arquivo
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * 
     * @return o item pai. Funciona apenas com reports do FTK1.
     */
    public EvidenceFile getEmailPai() {
        return emailPai;
    }

    /**
     * @return nome e caminho relativo ao caso com que o arquivo de evidência em
     *         si foi exportado
     */
    public String getExportedFile() {
        return exportedFile;
    }

    /**
     * 
     * @return a extensão original do item
     */
    public String getExt() {
        return extension;
    }

    /**
     * Módulos de processamento podem
     * setar atributos extras no item para armazenar o resultado do processamento
     * 
     * @param key o nome do atributo extra
     * @return o valor do atributo extra
     */
    public Object getExtraAttribute(String key) {
        return extraAttributes.get(key);
    }

    /**
     * 
     * @return o mapa de atributos extras do item. Módulos de processamento podem
     * setar atributos extras no item para armazenar o resultado do processamento
     */
    public Map<String, Object> getExtraAttributeMap() {
        return this.extraAttributes;
    }

    /**
     * 
     * @return o arquivo com o conteúdo do item. Retorna não
     * nulo apenas em processamentos de pastas, reports e no caso
     * de subitens de containers. Consulte {@link #getTempFile()}}
     * e {@link #getStream()}
     */
    public File getFile() {
        return file;
    }

    /**
     * 
     * @return o offset no item pai da onde o item foi recuperado (carving).
     * Retorna -1 se o item não é proveniente de carving.
     */
    public long getFileOffset() {
        return startOffset;
    }

    /**
     * 
     * @return o caminho para o arquivo do item. Diferente de vazio
     * apenas em reports e processamentos de pastas.
     */
    public String getFileToIndex() {
        if (alternativeFile != null)
            return alternativeFile.trim();
        else if (exportedFile != null)
            return exportedFile.trim();
        else
            return "";
    }

    /**
     * 
     * @return o id do item no FTK3+ no caso de reports
     */
    public String getFtkID() {
        return ftkID;
    }

    /**
     * 
     * @return o hash do arquivo, caso existente.
     */
    public String getHash() {
        return hash;
    }

    /**
     * 
     * @return o id do item
     */
    public int getId() {
        if (id == -1)
            id = Counter.getNextId();

        return id;
    }

    /**
     * 
     * @return os marcadores do item concatenados
     */
    public String getLabels() {
        return labels;
    }

    /**
     * @return tamanho do arquivo em bytes
     */
    public Long getLength() {
        return length;
    }

    /**
     * 
     * @return o mediaType do arquivo, resultado da análise de assinatura
     */
    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return data da última modificação do arquivo
     */
    public Date getModDate() {
        return modificationDate;
    }

    /**
     * @return nome do arquivo
     */
    public String getName() {
        return name;
    }

    /**
     * 
     * @return o id do item pai. Tem o nome do caso prefixado
     * no caso de reports do FTK3+
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * 
     * @return lista contendo os ids dos itens pai
     */
    public List<Integer> getParentIds() {
        return parentIds;
    }

    /**
     * 
     * @return ids dos itens pai concatenados com espaço
     */
    public String getParentIdsString() {
        String parents = "";
        for (Integer id : parentIds)
            parents += id + " ";

        return parents;
    }

    /**
     * 
     * @return o texto extraído do item armazenado pela tarefa de expansão
     * para alguns containers com texto (eml, ppt, etc)
     */
    public String getParsedTextCache() {
        return parsedTextCache;
    }

    /**
     * @return String com o caminho completo do item
     */
    public String getPath() {
        if (pathString != null)
            return pathString;
        else if (path != null)
            return path.getFullPath();
        else
            return null;
    }

    /**
     * @return caminho do arquivo, obtido na estrutura de árvore de arquivos do
     *         caso. (apenas para reports do FTK1)
     */
    public PathNode getPathNode() {
        return path;
    }

    /**
     * 
     * @return o objeto do Sleuthkit que representa o item
     */
    public AbstractFile getSleuthFile() {
        return sleuthFile;
    }

    /**
     * 
     * @return o id do item no Sleuthkit
     */
    public String getSleuthId() {
        return sleuthId;
    }

    /**
     * @return InputStream com o conteúdo do arquivo.
     */
    public InputStream getStream() throws IOException {
        if (tmpFile == null && tis != null && tis.hasFile())
            tmpFile = tis.getFile();
        if (tmpFile != null)
            return new FileInputStream(tmpFile);

        InputStream stream;
        if (file != null && !this.isDir)
            stream = new FileInputStream(file);

        else if (sleuthFile != null)
            stream = new ReadContentInputStream(sleuthFile);

        else
            stream = new ByteArrayInputStream(new byte[0]);

        if (startOffset != -1) {
            long skiped = 0;
            do {
                //OBS: skip usando ReadContentInputStream é eficiente pois utiliza seek
                //TODO implementar skip para File usando RandomAccessFile
                skiped += stream.skip(startOffset - skiped);
            } while (skiped < startOffset);

            stream = new LimitedInputStream(stream, length);
        }
        return stream;
    }

    /**
     * Usado em módulos que só possam processar um File e não um InputStream.
     * Pode impactar performance pois gera arquivo temporário.
     * 
     * @return um arquivo temporário com o conteúdo do item.
     * @throws IOException
     */
    public File getTempFile() throws IOException {
        if (startOffset == -1 && file != null)
            return file;
        if (tmpFile != null)
            return tmpFile;

        if (tis != null && tis.hasFile())
            tmpFile = tis.getFile();

        if (tmpFile == null)
            tmpFile = getTikaStream().getFile();

        return tmpFile;
    }

    /**
     * 
     * @return um TikaInputStream com o conteúdo do arquivo
     * @throws IOException
     */
    public TikaInputStream getTikaStream() throws IOException {
        if (tmpFile == null && tis != null && tis.hasFile())
            tmpFile = tis.getFile();
        if (tmpFile != null)
            tis = TikaInputStream.get(tmpFile);
        else if (startOffset == -1 && file != null && !this.isDir)
            tis = TikaInputStream.get(file);
        else
            tis = TikaInputStream.get(getBufferedStream());

        tmpResources.addResource(tis);
        return tis;
    }

    /**
     * @return o tipo de arquivo baseado na análise de assinatura
     */
    public EvidenceFileType getType() {
        return type;
    }

    /**
     * Obtém o arquivo de visualização. Se for nulo, retorna o arquivo
     * exportado. Apenas para reports do FTK1.
     * 
     * @return nome e caminho relativo ao caso do arquivo de para visualização
     */
    public String getViewFile() {
        return (viewFile == null) ? exportedFile : viewFile;
    }

    /**
     * 
     * @return true se o item tem filhos, como subitens ou itens carveados
     */
    public boolean hasChildren() {
        return hasChildren;
    }

    /**
     * 
     * @return true se o item é proveniente de carving
     */
    public boolean isCarved() {
        return carved;
    }

    /**
     * 
     * @return true se o item está apagado
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 
     * @return true se o item é um diretório
     */
    public boolean isDir() {
        return isDir;
    }

    /**
     * @return true se o item é uma duplicata de outro,
     * baseado no hash
     */
    public boolean isDuplicate() {
        return duplicate;
    }

    /**
     * @return true se o item está cifrado
     */
    public boolean isEncrypted() {
        return encrypted;
    }
    
    /**
     * @return true se o item foi submetido a parsing
     */
    public boolean isParsed() {
        return parsed;
    }
    
    /**
     * @return true se é um item de fim de fila de processamento
     */
    public boolean isQueueEnd() {
        return isQueueEnd;
    }
    
    /**
     * @param id identificador do item
     */
    public void setId(int id){
        this.id = id;
    }

    /**
     * @return true se é um item raiz
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * @return true se é um subitem de um container
     */
    public boolean isSubItem() {
        return isSubItem;
    }

    /**
     * @return true se o parsing do item ocasionou timeout
     */
    public boolean isTimedOut() {
        return timeOut;
    }

    /**
     * 
     * @return true se o item deve ser exportado
     */
    public boolean isToExtract() {
        return isToExtract;
    }

    /**
     * 
     * @return true se o item deve ser ignorado pelas próximas
     *      tarefas de processamento e excluído do caso
     */
    public boolean isToIgnore() {
        return toIgnore;
    }

    /**
     * 
     * @return true se o item deve ser adicionado ao caso
     */
    public boolean isToAddToCase() {
        return addToCase;
    }

    /**
     * Remove o item da categoria
     * 
     * @param category categoria a ser removida
     */
    public void removeCategory(String category) {
        categories.remove(category);
    }

    /**
     * @param accessDate
     *            nova data de último acesso
     */
    public void setAccessDate(Date accessDate) {
        this.accessDate = accessDate;
    }

    /**
     * @param alternativeFile
     *            nome e caminho do arquivo alternativo
     */
    public void setAlternativeFile(String alternativeFile) {
        this.alternativeFile = alternativeFile;
    }

    /**
     * Define se é um item de carving
     * 
     * @param carved se é item de carving
     */
    public void setCarved(boolean carved) {
        this.carved = carved;
    }

    /**
     * Redefine a categoria do item
     * 
     * @param category nova categoria
     */
    public void setCategory(String category) {
        categories = new HashSet<String>();
        categories.add(category);
    }

    /**
     * @param creationDate
     *            nova data de criação do arquivo
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Define se o item é apagado
     * 
     * @param deleted se é apagado
     */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Define se o item é duplicado
     * 
     * @param duplicate se é duplicado
     */
    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    /**
     * Define o email pai do item. Usado apenas em reports do FTK1.
     * 
     * @param pai email pai
     */
    public void setEmailPai(EvidenceFile pai) {
        emailPai = pai;
    }

    /**
     * Define se o item é cifrado
     * 
     * @param encrypted se é cifrado
     */
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    /**
     * Define o caminho para o arquivo do item, no caso de processamento de pastas
     * e para subitens extraídos.
     * 
     * @param exportedFile
     *            caminho para o arquivo do item
     */
    public void setExportedFile(String exportedFile) {
        this.exportedFile = exportedFile;
    }

    /**
     * Define a extensão do item.
     * 
     * @param ext extensão
     */
    public void setExtension(String ext) {
        extension = ext;
    }

    /**
     * Define um atributo extra para o item
     * @param key nome do atributo
     * @param value valor do atributo
     */
    public void setExtraAttribute(String key, Object value) {
        this.extraAttributes.put(key, value);
    }

    /**
     * Define o arquivo referente ao item, caso existente
     * 
     * @param file arquivo referente ao item
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Define o offset onde itens de carving são encontrados no item pai
     * 
     * @param fileOffset offset do item
     */
    public void setFileOffset(long fileOffset) {
        this.startOffset = fileOffset;
    }

    /**
     * Define o id do FTK3+, em casos de report
     * 
     * @param ftkID id do FTK
     */
    public void setFtkID(String ftkID) {
        this.ftkID = ftkID;
    }

    /**
     * Define se o item tem filhos, como subitens ou itens de carving
     * 
     * @param hasChildren se tem filhos
     */
    public void setHasChildren(boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    /**
     * Define o hash do item
     * 
     * @param hash hash do item
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    /**
     * Define se o item é um diretório.
     * 
     * @param isDir se é diretório
     */
    public void setIsDir(boolean isDir) {
        this.isDir = isDir;
    }

    /**
     * Define os marcadores do item
     * 
     * @param labels marcadores concatenados
     */
    public void setLabels(String labels) {
        this.labels = labels;
    }

    /**
     * @param length tamanho do arquivo
     */
    public void setLength(Long length) {
        this.length = length;
    }

    /**
     * Define o mediaType do item baseado na assinatura
     * @param mediaType internet mediaType
     */
    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    /**
     * @param modificationDate
     *            data da última modificação do arquivo
     */
    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    /**
     * @param name
     *            nome do arquivo
     */
    public void setName(String name) {
        this.name = name;
        int p = name.lastIndexOf(".");
        extension = (p < 0) ? "" : name.substring(p + 1).toLowerCase();
    }

    /**
     * @param parentId identificador do item pai
     */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /**
     * @param parsed se foi realizado parsing do item
     */
    public void setParsed(boolean parsed) {
        this.parsed = parsed;
    }

    /**
     * @param parsedTextCache texto extraído após o parsing
     */
    public void setParsedTextCache(String parsedTextCache) {
        this.parsedTextCache = parsedTextCache;
    }

    /**
     * @param path caminho do arquivo. Utilizado apenas em relatórios do FTK1
     */
    public void setPathNode(PathNode path) {
        this.path = path;
    }

    /**
     * @param path caminho do item
     */
    public void setPath(String path) {
        this.pathString = path;
    }

    /**
     * @param isQueueEnd se é um item especial de fim de fila
     */
    public void setQueueEnd(boolean isQueueEnd) {
        this.isQueueEnd = isQueueEnd;
    }

    /**
     * @param isRoot se o item é raiz
     */
    public void setRoot(boolean isRoot) {
        this.isRoot = isRoot;
    }

    /**
     * @param sleuthFile objeto que representa o item no sleuthkit
     */
    public void setSleuthFile(AbstractFile sleuthFile) {
        this.sleuthFile = sleuthFile;
    }

    /**
     * @param sleuthId id do item no sleuthkit
     */
    public void setSleuthId(String sleuthId) {
        this.sleuthId = sleuthId;
    }

    /**
     * @param isSubItem se o item é um subitem
     */
    public void setSubItem(boolean isSubItem) {
        this.isSubItem = isSubItem;
    }

    /**
     * @param timeOut se o parsing do item ocasionou timeout
     */
    public void setTimeOut(boolean timeOut) {
        this.timeOut = timeOut;
    }

    /**
     * @param isToExtract se o item deve ser extraído
     */
    public void setToExtract(boolean isToExtract) {
        this.isToExtract = isToExtract;
    }

    /**
     * @param toIgnore se o item deve ser ignorado pela tarefas
     *  de processamento seguintes e excluído do caso
     */
    public void setToIgnore(boolean toIgnore) {
        this.toIgnore = toIgnore;
    }

    /**
     * @param addToCase se o item deve ser adicionado ao caso
     */
    public void setAddToCase(boolean addToCase) {
        this.addToCase = addToCase;
    }

    /**
     * @param type tipo de arquivo
     */
    public void setType(EvidenceFileType type) {
        this.type = type;
    }

    /**
     * @param viewFile caminho do arquivo para visualização. Utilizado apenas
     * em relatórios do FTK1.
     */
    public void setViewFile(String viewFile) {
        this.viewFile = viewFile;
    }

    /**
     * Retorna String com os dados contidos no objeto.
     * 
     * @return String listando as propriedades do arquivo.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Arquivo: " + name);
        sb.append("\n\t\tCaminho: ").append(getPath());
        if (type != null)
            sb.append("\n\t\t").append("Tipo de Arquivo: ")
                    .append(type.getLongDescr());
        if (creationDate != null)
            sb.append("\n\t\tData de Criação: ").append(
                    FormatUtil.format(creationDate));
        if (modificationDate != null)
            sb.append("\n\t\tData de Modificação: ").append(
                    FormatUtil.format(modificationDate));
        if (accessDate != null)
            sb.append("\n\t\tData de Último Acesso: ").append(
                    FormatUtil.format(accessDate));
        if (length != null)
            sb.append("\n\t\tTamanho do Arquivo: ").append(length);
        return sb.toString();
    }

}
