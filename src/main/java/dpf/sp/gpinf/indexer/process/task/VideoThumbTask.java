/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.video.VideoProcessResult;
import gpinf.video.VideoThumbsMaker;
import gpinf.video.VideoThumbsOutputConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;

/**
 * Tarefa de geração de imagem com miniaturas (thumbs) de cenas extraídas de 
 * arquivos de vídeo.
 * @author Wladimir Leite
 */
public class VideoThumbTask extends AbstractTask {
    /** Instância da classe reponsável pelo processo de geração de thumbs. */
    private VideoThumbsMaker videoThumbsMaker;

    /** Lista de configurações de extração a serem geradas por vídeo, atualmente apenas uma imagem/configuração é utilizada. */
    private List<VideoThumbsOutputConfig> configs;

    /** Configuração de extração de cenas. */
    private VideoThumbsOutputConfig config;

    /** Pasta de saída das imagens. */
    private File baseFolder;

    /** Pasta temporária, utilizada como saído do MPlayer na extração de frames. */
    private File tmpFolder;

    /** Nome do arquivo temporário de miniatura gerado. Após conclusão será renomeado pra nome definitivo. */
    private String tempSuffix;

    /** Indica se a tarefa está habilitada ou não. */
    private static boolean taskEnabled = false;

    /** Constante com o nome utilizado para o arquivo de propriedades. */
    private static final String configFileName = "VideoThumbsConfig.txt";

    /** Executável, incluindo caminho do MPlayer. */
    private static String mplayer = "mplayer";

    /** Largura da imagem das cenas geradas. */
    private static int width = 200;

    /** Número de colunas. */
    private static int columns = 3;

    /** Número de linhas. */
    private static int rows = 6;

    /** Timeout da primeira execução do MPlayer. É maior para tratar possível processamento de fontes. */
    private static int timeoutFirst = 180000;

    /** Timeout na chamada de obtenção de propriedades do vídeo. */
    private static int timeoutInfo = 10000;

    /** Timeout na chamada normal de processamento do vídeo. */
    private static int timeoutProcess = 15000;

    /** Opção de redirecionamento da saída do MPlayer para o log, apenas para depuração de problemas. */
    private static boolean verbose = false;

    /** Objeto estático de inicialização. Necessário para garantir que seja feita apenas uma vez. */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Construtor.
     */
    public VideoThumbTask(Worker worker) {
        super(worker);
    }

    /**
     * Inicializa a tarefa de processamento de vídeos.
     * Carrega configurações sobre o tamanho/layout a ser gerado e camimnho do MPlayer,
     * que é o programa responsável pela extração de frames.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        //Instância objeto responsável pela extração de frames e inicializa parâmetros de utilização
        videoThumbsMaker = new VideoThumbsMaker();

        //Inicializa pasta temporarária e sufixo de arquivos temporários
        tmpFolder = new File(System.getProperty("java.io.tmpdir"));
        tempSuffix = Thread.currentThread().getId() + ".tmp";

        //Inicialização sincronizada
        synchronized (init) {
            if (!init.get()) {
                //Verifica se tarefa está habilitada
                String value = confParams.getProperty("enableVideoThumbs");
                if (value != null && value.trim().equalsIgnoreCase("true")) {
                    taskEnabled = true;
                } else {
                    Log.info("Extração de Cenas de Vídeos Desabilitada.");
                    init.set(true);
                    return;
                }

                //Lê parâmetros do arquivo de configuração
                UTF8Properties properties = new UTF8Properties();
                File confFile = new File(confDir, configFileName);
                try {
                    properties.load(confFile);

                    //Caminho do MPlayer
                    value = properties.getProperty("MPlayer");
                    if (value != null) mplayer = value.trim();

                    //Layout
                    value = properties.getProperty("Layout");
                    if (value != null) {
                        String[] vals = value.trim().split(",");
                        if (vals.length == 3) {
                            width = Integer.parseInt(vals[0].trim());
                            columns = Integer.parseInt(vals[1].trim());
                            rows = Integer.parseInt(vals[2].trim());
                        }
                    }

                    //Verbose do MPlayer
                    value = properties.getProperty("Verbose");
                    if (value != null && value.trim().equalsIgnoreCase("true")) verbose = true;

                    //Timeouts
                    value = properties.getProperty("Timeouts");
                    if (value != null) {
                        String[] vals = value.trim().split(",");
                        if (vals.length == 3) {
                            timeoutFirst = 1000 * Integer.parseInt(vals[0].trim());
                            timeoutInfo = 1000 * Integer.parseInt(vals[1].trim());
                            timeoutProcess = 1000 * Integer.parseInt(vals[2].trim());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.error("Erro lendo arquivo de configuração de tarefa de extração de cenas de vídeos: " + confFile.getAbsolutePath());
                    taskEnabled = false;
                    init.set(true);
                    throw new RuntimeException("Erro lendo arquivo de configuração de extração de cenas de vídeos!");
                }

                //Testa se o MPlayer está funcionando
                videoThumbsMaker.setMplayer(mplayer);
                String vmp = videoThumbsMaker.getVersion();
                if (vmp == null) {
                    Log.error("MPLAYER NÃO PODE SER EXECUTADO!");
                    Log.error("MPlyer Configurado = " + mplayer);
                    Log.error("Verifique o caminho e tente executá-lo diretamente na linha de comando.");
                    taskEnabled = false;
                    init.set(true);
                    throw new RuntimeException("Erro na extração de cenas de vídeos: MPlayer não pode ser executado!");
                }
                Log.info("Extração de Cenas de Vídeos Habilitada.");
                Log.info("Versão do MPLAYER utilizada: " + vmp);
                init.set(true);
            }
        }

        //Não continua se tarefa foi desabilitada
        if (!taskEnabled) return;

        //Inicializa parâmetros
        videoThumbsMaker.setMplayer(mplayer);
        videoThumbsMaker.setVerbose(verbose);
        videoThumbsMaker.setTimeoutFirstCall(timeoutFirst);
        videoThumbsMaker.setTimeoutProcess(timeoutProcess);
        videoThumbsMaker.setTimeoutInfo(timeoutInfo);

        //Cria configuração de extração de cenas
        configs = new ArrayList<VideoThumbsOutputConfig>();
        configs.add(config = new VideoThumbsOutputConfig(null, width, columns, rows, 2));

        //Inicializa diretório de saída
        baseFolder = new File(output, "view");
        if (!baseFolder.exists()) baseFolder.mkdirs();
    }

    @Override
    public void finish() throws Exception {
    }

    /**
     * Método principal do processamento. 
     * Primeiramente verifica se o tipo de arquivo é vídeo.
     * Depois chama método da classe, informando o caminho do arquivo de entrada e 
     * caminho completo de destino.    
     */
    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        //Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || !isMediaTypeProcessed(evidence.getMediaType())) return;

        //Chama o método de extração de cenas
        File tmpFile = null;
        try {
            File outFile = getHashFile(baseFolder, evidence.getHash(), "jpg");
            tmpFile = new File(outFile.getParentFile(), evidence.getHash() + tempSuffix);
            config.setOutFile(tmpFile);
            VideoProcessResult r = videoThumbsMaker.createThumbs(evidence.getTempFile(), tmpFolder, configs);
            if (r.isSuccess()) {
                tmpFile.renameTo(outFile);
            }
        } catch (Exception e) {

        } finally {
            if (tmpFile != null && tmpFile.exists()) tmpFile.delete();
        }
    }

    /**
     * Verifica se um tipo de arquivo deve ser processado.
     * @param mediaType
     */
    private boolean isMediaTypeProcessed(MediaType mediaType) {
        return mediaType.getType().equals("video") || mediaType.getBaseType().equals("application/vnd.rn-realmedia");
    }

    /**
     * Cria caminho completo a partir da pasta base, hash e extensao, no formato:
     * "/base/0/1/01hhhhhhh.ext".
     */
    //TODO: Talvez migrar para outra classe para permitir reutilização...
    public static File getHashFile(File baseDir, String hash, String ext) {
        StringBuilder path = new StringBuilder();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        File parent = result.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        return result;
    }
}