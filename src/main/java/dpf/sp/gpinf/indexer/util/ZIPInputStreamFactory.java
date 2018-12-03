package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable{
    
    private static int MAX_MEM_BYTES = 1 << 24;

    private ZipFile zip;
    
    public ZIPInputStreamFactory(Path dataSource) {
        super(dataSource);
    }
    
    private synchronized void init() throws IOException {
        if(zip == null) {
            zip = new ZipFile(this.dataSource.toFile());
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        Path tmp = null;
        byte[] bytes = null;
        if(zip == null) init();
        ZipArchiveEntry zae = zip.getEntry(path);
        try(InputStream is = zip.getInputStream(zae)){
            if(zae.getSize() <= MAX_MEM_BYTES) {
                bytes = IOUtils.toByteArray(is);
            }else {
                tmp = Files.createTempFile("zip-stream", null);
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(ClosedChannelException e) {
            if(zip != null) zip.close();
            zip = null;
            if(tmp != null) Files.delete(tmp);
            throw e;
        }
        if(bytes != null) {
            return new SeekableFileInputStream(new SeekableInMemoryByteChannel(bytes));
        }
        final Path finalTmp = tmp;
        return new SeekableFileInputStream(finalTmp.toFile()) {
            @Override
            public void close() throws IOException {
                super.close();
                Files.delete(finalTmp);
            }
        };
    }

    @Override
    public void close() throws IOException {
        if(zip != null) zip.close();
    }
    
}
