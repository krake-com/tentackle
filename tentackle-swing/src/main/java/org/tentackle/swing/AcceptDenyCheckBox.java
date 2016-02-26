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

// Created on September 15, 2002, 2:45 PM

package org.tentackle.swing;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.tentackle.swing.plaf.PlafUtilities;




/**
 * A simple checkbox for ok/yes/allow (green) and cancel/no/deny (red).
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class AcceptDenyCheckBox extends FormCheckBox {

  private boolean invert;     // invert boolean value


  /**
   * Creates an AcceptDenyCheckBox.
   *
   * @param invert true to invert the logic
   */
  public AcceptDenyCheckBox(boolean invert) {
    setInvert(invert);
    setIcon(PlafUtilities.getInstance().getIcon("cancel_mini"));
    setSelectedIcon(PlafUtilities.getInstance().getIcon("ok_mini"));
  }

  /**
   * Creates an AcceptDenyCheckBox.
   */
  public AcceptDenyCheckBox() {
    this(false);
  }



  /**
   * Returns whether the logic is inverted.
   *
   * @return true if inverted
   */
  public boolean isInvert() {
    return invert;
  }

  /**
   * Sets the inversion of the logic.
   *
   * @param invert true to invert the logic, false is default
   */
  public void setInvert(boolean invert) {
    this.invert = invert;
  }




  /**
   * Creates a cell renderer using an AcceptDenyCheckBox.
   *
   * @param invert true to invert the logic
   * @return the cell renderer
   */
  public static TableCellRenderer getTableCellRenderer(boolean invert) {
    return new BooleanTableCellRenderer(invert);
  }

  /**
   * Creates a cell renderer using an AcceptDenyCheckBox.
   *
   * @return the cell renderer
   */
  public static TableCellRenderer getTableCellRenderer() {
    return getTableCellRenderer(false);
  }


  /**
   * Creates a table cell editor using an AcceptDenyCheckBox.
   *
   * @param invert true to invert the logic
   * @return the cell renderer
   */
  public static TableCellEditor getTableCellEditor(boolean invert) {
    AcceptDenyCheckBox box = new AcceptDenyCheckBox(invert);
    box.setHorizontalAlignment(JLabel.CENTER);
    box.setBackground(PlafUtilities.getInstance().getTableFocusCellBackgroundColor());
    return new FormComponentCellEditor(box);
  }

  /**
   * Creates a table cell editor using an AcceptDenyCheckBox.
   *
   * @return the cell renderer
   */
  public static TableCellEditor getTableCellEditor() {
    return getTableCellEditor(false);
  }



  // Boolean cell renderer

  @SuppressWarnings("serial")
  private static class BooleanTableCellRenderer extends FormTableCellRenderer {

    private boolean invert;
    private Icon cancelIcon;
    private Icon okIcon;

    BooleanTableCellRenderer(boolean invert) {
      super();
      this.invert = invert;
      cancelIcon = PlafUtilities.getInstance().getIcon("cancel_mini");
      okIcon = PlafUtilities.getInstance().getIcon("ok_mini");
    }

    @Override
    public Component getTableCellRendererComponent (JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column)  {

      Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setValue(null);
      setHorizontalAlignment(JLabel.CENTER);
      setVerticalAlignment(JLabel.CENTER);
      ((JLabel) getComponent()).setIcon((value != null && (Boolean) value) ^ invert ? okIcon : cancelIcon);
      return comp;
    }

  }


}
