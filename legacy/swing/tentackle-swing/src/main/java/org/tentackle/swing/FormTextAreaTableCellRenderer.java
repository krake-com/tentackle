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

// Created on October 4, 2002, 6:54 PM

package org.tentackle.swing;

import java.awt.Component;
import java.awt.Rectangle;
import javax.swing.JTable;



/**
 * A multi line text area table cell renderer.
 *
 * @author harald
 */
public class FormTextAreaTableCellRenderer extends FormTableCellRenderer {

  // flag to adjust the rowHeight of the JTable automatically
  private boolean autoRowHeight;


  /**
   * Creates a text area table cell renderer.
   */
  public FormTextAreaTableCellRenderer() {
    super(new FormTextArea() {

      /**
       * see DefaultTableCellRenderer
       */
      @Override
      public void updateUI() {
        super.updateUI();
        setForeground(null);
        setBackground(null);
      }

      /**
       * Overridden for performance reasons.
       */
      @Override
      public void validate() {
      }

      /**
       * Overridden for performance reasons.
       */
      @Override
      public void revalidate() {
      }

      /**
       * Overridden for performance reasons.
       */
      @Override
      public void repaint(long tm, int x, int y, int width, int height) {
      }

      /**
       * Overridden for performance reasons.
       */
      @Override
      public void repaint(Rectangle r) {
      }
    });

    FormTextArea textArea = (FormTextArea) getComponent();
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    textArea.setCellEditorUsage(true);
    textArea.setSmartEnter(false);
    textArea.setEditable(false);

    setAutoRowHeight(true);
  }



  @Override
  protected void setValue(Object value) {
    ((FormTextArea) getComponent()).setText(value == null ? "" : value.toString());
  }


  @Override
  protected String getText() {
    return ((FormTextArea) getComponent()).getText();
  }


  @Override
  protected void setHorizontalAlignment(int alignment) {
    ((FormTextArea) getComponent()).setHorizontalAlignment(alignment);
  }

  @Override
  protected void setVerticalAlignment(int alignment) {
    ((FormTextArea) getComponent()).setVerticalAlignment(alignment);
  }



  /**
   * Enables or disables automatic adjustment of the row height.
   *
   * @param autoRowHeight true if enabled
   */
  public void setAutoRowHeight(boolean autoRowHeight)  {
    this.autoRowHeight = autoRowHeight;
  }

  /**
   * Returns whether automatic adjustment of the row height is enabled.
   *
   * @return true if enabled
   */
  public boolean isAutoRowHeight() {
    return autoRowHeight;
  }



  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {

    FormTextArea textArea = (FormTextArea) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    // fix for JRE 1.4: sets the width in order to allow getPreferredSize() to work
    if (textArea.getWidth() == 0)  {
      // get the width of the table-column
      int width = table.getColumnModel().getColumn(column).getWidth();
      // set the width with some reasonable height. We use the current row-height
      textArea.setSize(width, table.getRowHeight(row));
    }

    if (autoRowHeight)  {
      int height = textArea.getPreferredSize().height;
      // make row height large enough.
      // the "<" instead of "!=" will enlarge the row to the largest
      // column (if more than one FormTextAreaTableCellRenderer in a table)
      if (table != null && table.getRowHeight(row) < height)  {
        table.setRowHeight(row, height);
      }
    }

    return textArea;
  }

}


