package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.util.PropertiesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class IcePDFViewer extends Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(IcePDFViewer.class);
    /**
     *
     */
    private static final long serialVersionUID = -4538119351386926692L;
    private volatile SwingController pdfController;
    private volatile JPanel viewerPanel;

    volatile int fitMode = DocumentViewController.PAGE_FIT_WINDOW_WIDTH;
    volatile int viewMode = DocumentViewControllerImpl.ONE_COLUMN_VIEW;

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/pdf");

    }

    @Override
    public String getName() {
        return "Pdf";
    }

    public IcePDFViewer() {
        super(new BorderLayout());

        System.setProperty("org.icepdf.core.imageReference", "scaled");
        System.setProperty("org.icepdf.core.ccittfax.jai", "true");
        System.setProperty("org.icepdf.core.minMemory", "150M");
        System.setProperty("org.icepdf.core.views.page.text.highlightColor", "0xFFFF00");
		//pode provocar crash da jvm
        //System.setProperty("org.icepdf.core.awtFontLoading", "true");

    }

    @Override
    public void init() {

        new File(System.getProperties().getProperty("user.home"), ".icesoft/icepdf-viewer").mkdirs();

        pdfController = new SwingController();
        pdfController.setIsEmbeddedComponent(true);
        pdfController.getDocumentViewController().getViewContainer().setFocusable(false);

        PropertiesManager propManager = new PropertiesManager(System.getProperties(), pdfController.getMessageBundle());
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION, "false");
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_TOOL, "false");
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ZOOM, "true");
        propManager.set(PropertiesManager.PROPERTY_SHOW_STATUSBAR, "false");
        propManager.set(PropertiesManager.PROPERTY_HIDE_UTILITYPANE, "true");
        propManager.set(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT, Integer.toString(fitMode));
		//propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_UTILITY, "false");
        // propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_PAGENAV, "true");
        // propManager.set("application.showLocalStorageDialogs", "NO");

        final SwingViewBuilder factory = new SwingViewBuilder(pdfController, propManager, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null, viewMode, fitMode);
		// SwingViewBuilder factory = new SwingViewBuilder(pdfController, viewMode, fitMode);

        final JPanel panel = this.getPanel();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                viewerPanel = factory.buildViewerPanel();
                panel.add(viewerPanel, BorderLayout.CENTER);
                panel.setMinimumSize(new Dimension());
            }
        });

    }

    @Override
    public void copyScreen(Component comp) {
        super.copyScreen(pdfController.getDocumentViewController().getViewContainer());
    }

    @Override
    public void dispose() {
        if (pdfController != null) {
            pdfController.dispose();
        }

    }

    @Override
    public void loadFile(final StreamSource content, final Set<String> highlightTerms) {

        pdfController.closeDocument();

        if (content == null) {
            return;
        }

        new Thread() {
            @Override
            public void run() {

                pdfController.openDocument(content.getFile().getAbsolutePath());

                if (fitMode != pdfController.getDocumentViewController().getFitMode()) {
                    pdfController.setPageFitMode(fitMode, true);
                }

                if (pdfController.isUtilityPaneVisible()) {
                    pdfController.setUtilityPaneVisible(false);
                }

                //resize to force redraw
                getPanel().setSize(getPanel().getWidth() + delta, getPanel().getHeight());
                delta *= -1;

                highlightText(highlightTerms);

            }
        }.start();

    }

    private int delta = 1;
    private ArrayList<Integer> hitPages;

    private void highlightText(Set<String> highlightTerms) {
        try {
            DocumentSearchController search = pdfController.getDocumentSearchController();
            search.clearAllSearchHighlight();
            if (highlightTerms.size() == 0) {
                return;
            }

            //Workaround to rendering problem whith the first page with hits
            Thread.sleep(500);

            boolean caseSensitive = false, wholeWord = true;
            for (String term : highlightTerms) {
                search.addSearchTerm(term, caseSensitive, wholeWord);
            }

            currentHit = -1;
            totalHits = 0;
            hitPages = new ArrayList<Integer>();
            for (int i = 0; i < pdfController.getDocument().getNumberOfPages(); i++) {
                int hits = search.searchHighlightPage(i);
                if (hits > 0) {
                    totalHits++;
                    hitPages.add(i);
                    if (totalHits == 1) {
                        pdfController.getDocumentViewController().setCurrentPageIndex(i);
                        //pdfController.updateDocumentView();
                        currentHit = 0;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.info("Erro/Interrupção do Highlight");
        }

    }

    @Override
    public void scrollToNextHit(boolean forward) {

        if (forward) {
            if (currentHit < totalHits - 1) {
                pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(++currentHit));
            }

        } else {
            if (currentHit > 0) {
                pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(--currentHit));
            }

        }
        // pdfController.updateDocumentView();

    }

}
