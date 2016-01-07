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


package org.tentackle.swing.print;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import org.tentackle.swing.FormTable;



/**
 * Printable FormTable that looks nice when printed.
 *
 * @author harald
 */

@SuppressWarnings("serial")
public class PrintTable extends FormTable {


  /**
   * Creates a printable formtable
   */
  public PrintTable() {
    super();
  }

  
  /**
   * Creates a printable formtable for a given data model.
   * 
   * @param model the data model
   */
  public PrintTable(TableModel model) {
    super(model);
  }
  
  

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to gain room for the header
   */
  @Override
  public Dimension getPreferredSize() {
    Dimension dim = super.getPreferredSize();
    dim.height += getRowHeight() + getRowMargin();
    return dim;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to adjust for the extra height of the header
   */
  @Override
  public void setPreferredSize(Dimension preferredSize)  {
    super.setPreferredSize(new Dimension(
            preferredSize.width, preferredSize.height - getRowHeight() - getRowMargin()));
  }


  /**
   * Overridden to print the header.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public void print (Graphics g)  {

    int headerHeight = getRowHeight() + getRowMargin(); // additional height

    // adjust size of table to get room for the header
    Dimension size = super.getSize();
    super.setSize(size.width, size.height - headerHeight);

    JTableHeader header = getTableHeader();

    PrintLabel pl = new PrintLabel();       // Label to print a header column
    pl.setHorizontalAlignment(JLabel.CENTER);
    pl.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    Font font = getFont();
    pl.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
    pl.setForeground(header.getForeground());
    pl.setBackground(header.getBackground());

    TableColumnModel model = header.getColumnModel();
    int columns = model.getColumnCount();
    int columnMargin = model.getColumnMargin();

    g.translate(columnMargin, 0);

    for (int i=0; i < columns; i++)  {

      TableColumn col = model.getColumn(i);
      Rectangle rect = header.getHeaderRect(i);
      rect.height = getRowHeight();
      pl.setBounds(rect);
      pl.setText(getColumnName(i));
      g.translate(rect.x, rect.y);
      pl.print(g);
      g.translate(-rect.x, -rect.y);
    }

    g.translate(0, headerHeight);   // move down after header
    super.print(g);                 // print the table

    g.translate(-columnMargin, -headerHeight);
    super.setSize(size);            // restore to original size
  }


}