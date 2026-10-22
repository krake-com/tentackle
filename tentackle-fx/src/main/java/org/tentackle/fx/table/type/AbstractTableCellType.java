/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.table.type;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableCellType;

import javafx.geometry.Pos;
import javafx.scene.control.TextField;

/**
 * Base class of a table and treetable cell type.
 *
 * @param <T> type of the content in a cell
 * @author harald
 */
public abstract class AbstractTableCellType<T> implements TableCellType<T> {

  /**
   * The editor, created lazily by {@link #getEditor()}.
   */
  protected FxComponent editor;

  /**
   * Parent constructor.
   */
  public AbstractTableCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Updates the alignment of the table cell's contents.
   *
   * @param tableCell the cell, never null
   * @param alignment the alignment suggested by the cell type, never null
   */
  public void updateAlignment(FxTableCell<?, T> tableCell, Pos alignment) {
    Pos configAlignment = tableCell.getColumnConfiguration().getAlignment();
    if (configAlignment != null) {
      alignment = configAlignment;
    }
    tableCell.setAlignment(alignment);
  }

  /**
   * Updates the alignment of the treetable cell's contents.
   *
   * @param treeTableCell the cell, never null
   * @param alignment the alignment suggested by the cell type, never null
   */
  public void updateAlignment(FxTreeTableCell<?, T> treeTableCell, Pos alignment) {
    Pos configAlignment = treeTableCell.getColumnConfiguration().getAlignment();
    if (configAlignment != null) {
      alignment = configAlignment;
    }
    treeTableCell.setAlignment(alignment);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default editor is a text field.
   */
  @Override
  public FxComponent getEditor() {
    if (editor == null) {
      editor = Fx.create(TextField.class);
    }
    return editor;
  }

  @Override
  public Class<?> getEditorType() {
    return String.class;
  }


  /**
   * Applies the case conversion.
   *
   * @param caseConversion true = convert to uppercase, false = lowercase, null = no conversion (default)
   * @param text the original text
   * @return the converted text
   */
  protected String applyCaseConversion(Boolean caseConversion, String text) {
    if (text != null) {
      if (Boolean.TRUE.equals(caseConversion)) {
        text = text.toUpperCase();
      }
      else if (Boolean.FALSE.equals(caseConversion)) {
        text = text.toLowerCase();
      }
    }
    return text;
  }

}
