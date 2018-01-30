/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


// Created on October 27, 2002, 1:16 PM

package org.tentackle.swing;

import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;



/**
 * A renderer for a cell in the table header.
 */
public class FormTableHeaderCellRenderer extends JLabel implements ListCellRenderer {

  /**
   * Creates a header cell renderer.<br>
   * @param table the table to which that header cell belongs
   */
  public FormTableHeaderCellRenderer(JTable table) {
    JTableHeader header = table.getTableHeader();
    setOpaque(true);
    setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    setHorizontalAlignment(CENTER);
    setForeground(header.getForeground());
    setBackground(header.getBackground());
    setFont(header.getFont());
  }

  /**
   * {@inheritDoc}
   * <p>
   * To change the look of a cell override this method!
   **/
  public Component getListCellRendererComponent(JList list, Object value, int index,
                                                boolean isSelected, boolean cellHasFocus) {
    setText((value == null) ? "" : value.toString());
    return this;
  }
}
