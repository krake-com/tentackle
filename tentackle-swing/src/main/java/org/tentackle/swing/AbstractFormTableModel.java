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


package org.tentackle.swing;

import javax.swing.table.AbstractTableModel;


/**
 * Abstract table model that maintains a chained mapping of rows
 * by a {@link FormTableMap}.
 *
 * @param <T> the type handled by the model
 * @author harald
 */
@SuppressWarnings("serial")
abstract public class AbstractFormTableModel<T> extends AbstractTableModel {

  private FormTable<T> table;   // the table, null if not the direct model of table (in chain)
  private FormTableMap<T> map;  // != null if map is ahead of chain
  private boolean dataChanged;  // true if data has been changed


  /**
   * Sets the table.
   *
   * @param table the form table or null if this model is not the direct data model for the table
   *        (i.e. somewhere in a chain)
   */
  public void setTable(FormTable<T> table) {
    this.table = table;
  }

  /**
   * Gets the table.
   *
   * @return the form table or null if this model is not the direct data model for the table
   */
  public FormTable<T> getTable() {
    return table;
  }


  /**
   * Sets the chained mapper.
   *
   * @param map the chained form table map or null if this model is not chaining another map
   */
  public void setMap(FormTableMap<T> map) {
    this.map = map;
  }

  /**
   * Sets the chained mapper.
   * @return the chained form table map or null if this model is not chaining another map
   */
  public FormTableMap<T> getMap() {
    return map;
  }


  /**
   * Gets the form table entry at given row.
   *
   * @param rowIndex the row of the table entry
   * @return the table entry, null if rowIndex out of range
   */
  abstract public FormTableEntry<T> getEntryAt(int rowIndex);


  /**
   * Gets the table entry template from the model.
   *
   * @return the form table entry template
   */
  abstract public FormTableEntry<T> getTemplate();


  /**
   * Sets the form table entry at given row.<br>
   * The default implementation just triggers a value changed event
   * for the table.
   * Must be overridden for another mapping than 1:1.
   *
   * @param entry the formtable entry
   * @param rowIndex the row of the table entry
   * @return true if replaced, false if not (rowIndex out of range)
   */
  public boolean setEntryAt(FormTableEntry<T> entry, int rowIndex) {
    if (table != null && isDataChanged()) {
      FormUtilities.getInstance().triggerValueChanged(table);
    }
    return true;
  }


  /**
   * Gets the row in the original model according to the
   * mapping.<br>
   * The default implementation returns the same row.
   * Must be overridden for another mapping than 1:1.
   * @param row the row of this model
   * @return the row of the mapped model
   */
  public int getModelIndex(int row) {
    return row;
  }


  /**
   * Gets the rows in the original model according to the
   * mapping.<br>
   * The default implementation returns the same rows.
   * Must be overridden for another mapping than 1:1.
   * @param rows the array of rows of this model
   * @return the rows of the mapped model
   */
  public int[] getModelIndex(int[] rows) {
    return rows;
  }


  /**
   * Gets the mapped row index according to a given model row.<br>
   * Inversion of getModelIndex().
   * The default implementation returns the same row.
   * Must be overridden for another mapping than 1:1.
   * @param mappedRow the original row
   * @return the mapped row index
   */
  public int getMappedIndex(int mappedRow) {
    return mappedRow;
  }


  /**
   * Gets the mapped row indexes according to given model rows.<br>
   * Inversion of getModelIndex().
   * The default implementation returns the same rows.
   * Must be overridden for another mapping than 1:1.
   * @param mappedRows the original rows
   * @return the mapped row indexes
   */
  public int[] getMappedIndex(int[] mappedRows) {
    return mappedRows;
  }


  /**
   * Returns whether data has changed.
   * Used to for the famous "discard any changes?"-question.
   *
   * @return true if model data has been changed
   */
  public boolean isDataChanged() {
    return dataChanged;
  }


  /**
   * Sets a flag that model data has changed.
   * Will be cleared on listChanged() or cursorChanged().
   *
   * @param dataChanged true if model data has changed
   */
  public void setDataChanged(boolean dataChanged) {
    this.dataChanged = dataChanged;
  }


  /**
   * Gets the displayed column name.
   *
   * @param column column index
   * @return the column name
   */
  public abstract String getDisplayedColumnName (int column);

}
