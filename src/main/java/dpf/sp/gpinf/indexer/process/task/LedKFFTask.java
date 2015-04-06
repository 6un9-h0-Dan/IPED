package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.FileGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

public class LedKFFTask extends AbstractTask{
    
    private static String ledCategory = "Alerta de Hash";
    
    private static HashValue[] hashArray = new HashValue[0];

    public LedKFFTask(Worker worker) {
        super(worker);
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized(hashArray){
            if(hashArray.length != 0)
                return;
            
            this.caseData.addBookmark(new FileGroup(ledCategory, "", ""));
            String hash = confParams.getProperty("hash");
            String ledWkffPath = confParams.getProperty("ledWkffPath");
            if(ledWkffPath == null)
                return;
            File wkffDir = new File(ledWkffPath);
            ArrayList<HashValue> hashList = new ArrayList<HashValue>();
            
            for(File wkffFile : wkffDir.listFiles()){
                BufferedReader reader = new BufferedReader(new FileReader(wkffFile));
                String line = reader.readLine();
                while((line = reader.readLine()) != null){
                    String[] hashes = line.split(" \\*");
                    if(hash.equals("md5"))
                        hashList.add(new HashValue(hashes[0].trim()));
                    else if(hash.equals("sha-1"))
                        hashList.add(new HashValue(hashes[3].trim()));
                }
                reader.close();
            }
            hashArray = hashList.toArray(new HashValue[0]);
            hashList = null;
            Arrays.sort(hashArray);
        }
        
    }

    @Override
    public void finish() throws Exception {
        hashArray = null;
        
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        String hash = evidence.getHash();
        if(hash != null){
            if(Arrays.binarySearch(hashArray, new HashValue(hash)) >= 0)
                evidence.addCategory(ledCategory);
                
        }
        
    }

}
