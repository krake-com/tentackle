/**
 * Tentackle - http://www.tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package org.tentackle.swing.plaf;

import org.tentackle.swing.FormTable;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import javax.swing.plaf.UIResource;

/**
 * Transfer handler for FormTables.
 * <p>
 * Differs from original transferhandler in that drag can be enabled to drag cells
 * instead of rows. To enable this feature, the FormTable must setCellDragEnabled(true),
 * which is true by default as this is 99%-case.
 *
 * @author harald
 */
public class TTableTransferHandler extends TransferHandler implements UIResource {

  @Override
  protected Transferable createTransferable(JComponent c) {
    if (c instanceof JTable) {
      JTable table = (JTable) c;
      int[] rows;
      int[] cols;

      if (!table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed()) {
        return null;
      }

      if (!table.getRowSelectionAllowed()) {
        int rowCount = table.getRowCount();
        rows = new int[rowCount];
        for (int counter = 0; counter < rowCount; counter++) {
          rows[counter] = counter;
        }
      }
      else {
        rows = table.getSelectedRows();
      }

      if (!table.getColumnSelectionAllowed()) {
        // HARR
        if (table instanceof FormTable && ((FormTable)table).isCellDragEnabled() &&
            table.getColumnModel().getSelectionModel().getAnchorSelectionIndex() >= 0) {
          cols = new int[1];
          cols[0] = table.getColumnModel().getSelectionModel().getAnchorSelectionIndex();
        }
        // /HARR
        else  {
          int colCount = table.getColumnCount();
          cols = new int[colCount];
          for (int counter = 0; counter < colCount; counter++) {
            cols[counter] = counter;
          }
        }
      }
      else {
        cols = table.getSelectedColumns();
      }

      if (rows.length == 0 || cols.length == 0) {
        return null;
      }

      StringBuilder plainBuf = new StringBuilder();
      StringBuilder htmlBuf = new StringBuilder();

      htmlBuf.append("<html>\n<body>\n<table>\n");

      for (int row = 0; row < rows.length; row++) {
        htmlBuf.append("<tr>\n");
        for (int col = 0; col < cols.length; col++) {
          Object obj = table.getValueAt(rows[row], cols[col]);
          String val = ((obj == null) ? "" : obj.toString());
          plainBuf.append(val).append("\t");
          htmlBuf.append("  <td>").append(val).append("</td>\n");
        }
        // we want a newline at the end of each line and not a tab
        plainBuf.deleteCharAt(plainBuf.length() - 1).append("\n");
        htmlBuf.append("</tr>\n");
      }

      // remove the last newline
      plainBuf.deleteCharAt(plainBuf.length() - 1);
      htmlBuf.append("</table>\n</body>\n</html>");

      return new TBasicTransferable(plainBuf.toString(), htmlBuf.toString());
    }

    return null;
  }

  @Override
  public int getSourceActions(JComponent c) {
      return COPY;
  }

}
