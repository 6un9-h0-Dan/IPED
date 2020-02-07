package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.io.File;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlLinkViewer.AttachmentSearcher;
import iped3.IItemId;

public class AttachmentSearcherImpl implements AttachmentSearcher {

    @Override
    public File getTmpFile(String luceneQuery) {

        IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, luceneQuery);
        try {
            MultiSearchResult result = searcher.multiSearch();
            if (result.getLength() == 0)
                return null;
            IItemId item = result.getItem(0);
            File file = App.get().appCase.getItemByItemId(item).getTempFile();
            return file;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
