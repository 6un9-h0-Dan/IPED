package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import dpf.sp.gpinf.indexer.search.TextParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
<<<<<<< HEAD
<<<<<<< HEAD
import dpf.sp.gpinf.indexer.util.StreamSource;
import java.util.Set;


public class TextViewer extends ATextViewer {
  
  public TextViewer(AppSearchParams params) {
    super(params);
  }

  @Override
  public void loadFile(StreamSource content, String contentType, Set<String> highlightTerms) {

    if (content == null) {
      loadFile(content, null);
    } else {
      textParser = new TextParser(appSearchParams, content, contentType, tmp);
      textParser.execute();
    }

  }
=======
import dpf.sp.gpinf.indexer.ui.fileViewer.util.TextParser;

public class TextViewer extends Viewer implements KeyListener, MouseListener {

  public static Font font = new Font("Courier New", Font.PLAIN, 11);
=======
import dpf.sp.gpinf.indexer.util.StreamSource;
import java.util.Set;
>>>>>>> 85a3db0... Desmembramento do viewer para outro projeto.


public class TextViewer extends ATextViewer {
  
  public TextViewer(AppSearchParams params) {
    super(params);
  }

  @Override
  public void loadFile(StreamSource content, String contentType, Set<String> highlightTerms) {

    if (content == null) {
      loadFile(content, null);
    } else {
      textParser = new TextParser(appSearchParams, content, contentType, tmp);
      textParser.execute();
    }

  }

<<<<<<< HEAD
  public class TextViewerModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public int getRowCount() {
      if (textParser != null) {
        try {
          int lines = textParser.viewRows.size() - 1;
          if (lines == App.MAX_LINES) {
            lines = App.MAX_LINES + (int) ((TextParser.parsedFile.size() - textParser.viewRows.get(App.MAX_LINES)) / App.MAX_LINE_SIZE) + 1;
          }
          return lines;

        } catch (Exception e) {
        }
      }
      return 0;
    }

    @Override
    public String getColumnName(int col) {
      return "";
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return false;
    }

    @Override
    public Object getValueAt(int row, int col) {
      try {
        long off = 0, len;
        if (row < App.MAX_LINES) {
          off = textParser.viewRows.get(row);
          len = textParser.viewRows.get(row + 1) - off;
        } else {
          off = textParser.viewRows.get(App.MAX_LINES) + (long) (row - App.MAX_LINES) * App.MAX_LINE_SIZE;
          len = App.MAX_LINE_SIZE;

          // Tratamento para não dividir hits destacados
          // Desloca início da linha para final de fragmento com hit
          Long hitOff = textParser.sortedHits.floorKey(off);
          if (hitOff != null) {
            int[] hit = textParser.sortedHits.get(hitOff);
            if (hitOff < off && hitOff + hit[0] > off) {
              len -= (hitOff + hit[0] - off);
              if (len < 0) {
                len = 0;
              }
              off = hitOff + hit[0];
            }
          }
          // estende linha até final do fragmento com hit
          hitOff = textParser.sortedHits.floorKey(off + len);
          if (hitOff != null) {
            int[] hit = textParser.sortedHits.get(hitOff);
            if (hitOff < off + len && hitOff + hit[0] > off + len) {
              len = hitOff + hit[0] - off;
            }
          }

          if (off + len > TextParser.parsedFile.size()) {
            len = TextParser.parsedFile.size() - off;
          }
        }

        ByteBuffer data = ByteBuffer.allocate((int) len);
        int nread;
        do {
          nread = TextParser.parsedFile.read(data, off);
          off += nread;
        } while (nread != -1 && data.hasRemaining());

        data.flip();
        String line = (new String(data.array(), "windows-1252")).replaceAll("\n", " ").replaceAll("\r", " ");/*
         * .
         * replaceAll
         * (
         * "\t"
         * ,
         * "&#09;"
         * )
         * .
         * replaceAll
         * (
         * "  "
         * ,
         * "&nbsp;&nbsp; "
         * )
         */

        return "<html><pre>" + line + "</pre></html>";

      } catch (Exception e) {
        // e.printStackTrace();
        return "";
      }

    }

  }

  int keyBefore = -1;

  @Override
  public void keyPressed(KeyEvent e) {
  }

  @Override
  public void keyReleased(KeyEvent evt) {
    if (textTable.getSelectedRow() == -1) {
      return;
    }

    if ((keyBefore == KeyEvent.VK_CONTROL && evt.getKeyCode() == KeyEvent.VK_C) || (keyBefore == KeyEvent.VK_C && evt.getKeyCode() == KeyEvent.VK_CONTROL)) {
      StringBuilder copy = new StringBuilder();
      for (Integer row : textTable.getSelectedRows()) {
        String value = textViewerModel.getValueAt(row, 0).toString();
        value = value.replaceAll("<html><pre>", "").replaceAll("</pre></html>", "");
        value = value.replaceAll(App.get().getParams().HIGHLIGHT_START_TAG, "").replaceAll(App.get().getParams().HIGHLIGHT_END_TAG, "");
        value = LuceneSimpleHTMLEncoder.htmlDecode(value);
        copy.append(value + "\r\n");
      }
      StringSelection stringSelection = new StringSelection(copy.toString());
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(stringSelection, stringSelection);
    }
    keyBefore = evt.getKeyCode();
  }

  @Override
  public void keyTyped(KeyEvent e) {
  }

  @Override
  public void scrollToNextHit(boolean forward) {

    currentHit = App.get().hitsTable.getSelectedRow();
    totalHits = textParser.hits.size();
    if (forward) {
      if (currentHit < totalHits - 1) {
        App.get().hitsTable.setRowSelectionInterval(currentHit + 1, currentHit + 1);
      }

    } else {
      if (currentHit > 0) {
        App.get().hitsTable.setRowSelectionInterval(currentHit - 1, currentHit - 1);
      }

    }
    App.get().hitsTable.scrollRectToVisible(App.get().hitsTable.getCellRect(App.get().hitsTable.getSelectionModel().getLeadSelectionIndex(), 0, false));

  }

  @Override
  public void mouseClicked(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseEntered(MouseEvent arg0) {
    ViewerControl viewerControl = ViewerControlImpl.getInstance();
    viewerControl.releaseLibreOfficeFocus();
  }

  @Override
  public void mouseExited(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mousePressed(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseReleased(MouseEvent arg0) {
    // TODO Auto-generated method stub

  }
>>>>>>> 4855b2f... Versão estável do desmembramento por pacote.

=======
>>>>>>> 85a3db0... Desmembramento do viewer para outro projeto.
}
