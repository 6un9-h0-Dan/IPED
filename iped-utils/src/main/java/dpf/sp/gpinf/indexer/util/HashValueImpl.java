package dpf.sp.gpinf.indexer.util;

import java.io.Serializable;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import iped3.HashValue;

public class HashValueImpl implements HashValue, Comparable<HashValue>, Serializable {

    private static final long serialVersionUID = 1L;

    private byte[] bytes;

    public HashValueImpl(byte[] bytes) {
        this.bytes = bytes;
    }

    public HashValueImpl(String hash) {
        try {
            bytes = Hex.decodeHex(hash.toCharArray());

        } catch (DecoderException e) {
            e.printStackTrace();
        }
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String toString() {
        return new String(Hex.encodeHex(bytes, false));
    }

    @Override
    public int compareTo(HashValue hash) {
        byte[] compBytes = hash.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            int cmp = Integer.compare(bytes[i] & 0xFF, compBytes[i] & 0xFF);
            if (cmp != 0)
                return cmp;
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
