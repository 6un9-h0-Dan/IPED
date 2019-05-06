package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.HashValueImpl;
import iped3.Item;
import iped3.HashValue;

/**
 * Tarefa de verificação de arquivos duplicados. Ignora o arquivo caso configurado.
 *
 */
public class DuplicateTask extends AbstractTask {

  public static String HASH_MAP = HashTask.class.getSimpleName() + "HashMap"; //$NON-NLS-1$

  private HashMap<HashValue, HashValue> hashMap;

  public static boolean ignoreDuplicates = false;

  public void process(Item evidence) {

    // Verificação de duplicados
    HashValue hashValue = evidence.getHashValue();
    if (hashValue != null) {
      synchronized (hashMap) {
        if (!hashMap.containsKey(hashValue)) {
          hashMap.put(hashValue, hashValue);
        } else {
          evidence.setDuplicate(true);
        }

      }
    }

    if (ignoreDuplicates && evidence.isDuplicate() && !evidence.isDir() && !evidence.isRoot() && !caseData.isIpedReport()) {
      evidence.setToIgnore(true);
    }

  }

  @Override
  public void init(Properties confProps, File confDir) throws Exception {

    String value = confProps.getProperty("ignoreDuplicates"); //$NON-NLS-1$
    if (value != null) {
      value = value.trim();
    }
    if (value != null && !value.isEmpty()) {
      ignoreDuplicates = Boolean.valueOf(value);
    }

    hashMap = (HashMap<HashValue, HashValue>) caseData.getCaseObject(HASH_MAP);
    if (hashMap == null) {
      hashMap = new HashMap<HashValue, HashValue>();
      caseData.putCaseObject(HASH_MAP, hashMap);

      File indexDir = new File(worker.output, "index"); //$NON-NLS-1$
      if (indexDir.exists() && indexDir.list().length > 5) {
        IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
        for (int i = 0; i < reader.maxDoc(); i++) {
          Document doc = reader.document(i);
          String hash = doc.get(IndexItem.HASH);
          if (hash != null && !hash.isEmpty()) {
            HashValue hValue = new HashValueImpl(hash);
            hashMap.put(hValue, hValue);
          }

        }
        reader.close();
      }
    }

  }

  @Override
  public void finish() throws Exception {
    // TODO Auto-generated method stub

  }

}
