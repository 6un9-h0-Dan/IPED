/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.IOUtil;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe para calcular e manipular hashes.
 */
public class HashTask extends AbstractTask{

	private static Logger LOGGER = LoggerFactory.getLogger(HashTask.class);
	private MessageDigest digest;
	private String algorithm;	
	
	public HashTask(Worker worker){
		super(worker);
	}
	
	@Override
	public void init(Properties confProps, File confDir) throws Exception {
		String value = confProps.getProperty("hash");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty()){
			this.algorithm = value.toUpperCase();
			this.digest = MessageDigest.getInstance(algorithm);
		}
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

	public static class HashValue implements Comparable<HashValue>, Serializable{

		byte[] bytes;

		/*public HashValue(){
		    
		}*/
		
		public HashValue(String hash) {
			bytes = DatatypeConverter.parseHexBinary(hash);
		}
		
		public String toString(){
			return DatatypeConverter.printHexBinary(bytes);
		}

		@Override
		public int compareTo(HashValue hash) {
			for (int i = 0; i < bytes.length; i++) {
				if ((bytes[i] & 0xFF) < (hash.bytes[i] & 0xFF))
					return -1;
				else if ((bytes[i] & 0xFF) > (hash.bytes[i] & 0xFF))
					return 1;
			}
			return 0;
		}

		@Override
		public boolean equals(Object hash) {
			return compareTo((HashValue) hash) == 0;
		}

		@Override
		public int hashCode() {
			return bytes[3] & 0xFF | (bytes[2] & 0xFF) << 8 | (bytes[1] & 0xFF) << 16 | (bytes[0] & 0xFF) << 24;
		}
		
	}

	public void process(EvidenceFile evidence) {

		if (digest != null && evidence.getHash() == null && !evidence.isQueueEnd()) {
			
			InputStream in = null;
			try {
				in = evidence.getBufferedStream();
				byte[] hash = compute(in);
				evidence.setHash(getHashString(hash));
				
				// save(hash, IOUtil.getRelativePath(output,
				// evidence.getFile()));
				// save(hash, evidence.getPath());

			} catch (Exception e) {
				LOGGER.warn("{} Erro ao calcular hash {}\t{}", Thread.currentThread().getName(), evidence.getPath(), e.toString());
				//e.printStackTrace();
				
			} finally {
				IOUtil.closeQuietly(in);
			}
		}
		

	}
	
	

	public byte[] compute(InputStream in) throws IOException {
		byte[] buf = new byte[1024 * 1024];
		int len;
		while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted())
			digest.update(buf, 0, len);

		byte[] hash = digest.digest();
		return hash;
	}

	public static String getHashString(byte[] hash) {
		StringBuilder result = new StringBuilder();
		for (byte b : hash)
			result.append(String.format("%1$02X", b));

		return result.toString();
	}

}
