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


// Created on November 19, 2002, 6:23 PM



package org.tentackle.swing;

import java.util.Enumeration;
import java.util.Vector;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;


/**
 * Extended table column model.<br>
 * Adds the feature to display/hide a column by model index.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableColumnModel extends DefaultTableColumnModel {

  /** 
   * all columns whether visible or not are stored here.
   * The superclass holds the visible columns only.
   */
  protected Vector<TableColumn> originalColumns;
  
  
  
  /**
   * Creates a formtable column model.<br>
   * This is the standard constructor with no columns displayed.
   * Columns must be added via {@link #addColumn}.
   */
  public FormTableColumnModel() {
    super();
    originalColumns = new Vector<TableColumn>();
  }

  
  
  /**
   * Returns the original index of a table column.
   * 
   * @param column the table column
   * @return the original column index
   */
  public int getOriginalIndex(TableColumn column) {
    return originalColumns.indexOf(column);
  }
  
  /**
   * Maps between the visible and model indexes.
   * @param visibleIndex the visible index
   * @return the model index
   */
  public int getOriginalIndex(int visibleIndex) {
    TableColumn column = super.getColumn(visibleIndex);
    int orgIndex = getOriginalIndex(column);
    if (orgIndex == -1) {
      // indicates that somehow the underlying vector has been modified
      throw new IllegalArgumentException("no such original column");
    }
    return orgIndex;
  }
  
  
  /**
   * Gets the original column at a given model index 
   * @param originalIndex the original model index
   * @return the table column
   */
  public TableColumn getOriginalColumn(int originalIndex) {
    return originalColumns.get(originalIndex);
  }
  

  /**
   * Gets all original columns.
   * @return the columns
   */
  public Enumeration<TableColumn> getOriginalColumns() {
    return originalColumns.elements();
  }
  
  
  
  /**
   * Gets the column for the given data-modelindex
   * @param modelIndex the data model index
   * @return the table column
   */
  public TableColumn getColumnByModelIndex(int modelIndex)  {
    for (TableColumn column: originalColumns) {
      if (column.getModelIndex() == modelIndex) {
        return column;
      }
    }
    return null;
  }
  

  
  
  @Override
  public void addColumn(TableColumn aColumn) {
    super.addColumn(aColumn);
    originalColumns.add(aColumn);
  }

  @Override
  public void removeColumn(TableColumn column) {
    super.removeColumn(column);
    originalColumns.remove(column);
  }
  
  
  /**
   * Removes all columns, original and visible.
   */
  public void removeAllColumns()  {
    // remove all visible columns
    while (tableColumns.size() > 0) {
      super.removeColumn(tableColumns.firstElement());
    }
    // all orginal
    originalColumns.removeAllElements();
  }

  
  
  
  /**
   * {@inheritDoc}
   * <p>
   * Overridden to move a column according to the visible index.<br>
   * The columns in the "original"-space are moved accordingly.
   */
  @Override
  public void moveColumn(int columnIndex, int newIndex) {
    TableColumn col = getColumn(columnIndex);
    // move in visible space (and possibly throw an exception)
    super.moveColumn(columnIndex, newIndex);
    if (columnIndex != newIndex)  {
      // remove this column from originals
      originalColumns.remove(col);
      // insert in original space among "visibles"
      int viewIndex = -1;
      boolean inserted = false;
      for (int i=0; i < originalColumns.size(); i++)  {
        if (isOriginalColumnVisible(i)) {
          viewIndex++;
          if (viewIndex == newIndex) {
            originalColumns.insertElementAt(col, i);
            inserted = true;
            break;
          }
        }
      }
      if (!inserted) {
        originalColumns.add(col); // append to end
      } // append to end
    }
  }

  
  /**
   * Checks if a column is visible.
   * @param originalIndex the original column index
   * @return true if visible
   * @see #isModelColumnVisible(int) 
   */
  public boolean isOriginalColumnVisible(int originalIndex) {
    // get the tablecolumn
    TableColumn column = originalColumns.get(originalIndex);   // will throw exception if out of bounds!
    // get the current visible index, -1 = not visible
    return (tableColumns.indexOf(column) >= 0);
  }
  
  
  /**
   * Checks if a column is visible.
   * @param modelIndex the model index
   * @return true if visible
   * @see #isOriginalColumnVisible(int) 
   */
  public boolean isModelColumnVisible(int modelIndex) {
    // get the tablecolumn
    TableColumn column = getColumnByModelIndex(modelIndex);
    // get the current visible index, -1 = not visible
    return (tableColumns.indexOf(column) >= 0);
  }
  
  
  
  /**
   * Sets a column invisible/visible.<br>
   * Removes/adds the column from/to the underlying vector
   * but keeps it in the original vector.
   * The index used is the original index, i.e. the columns as if they were ALL visible.
   * 
   * @param originalIndex the column index
   * @param visible true to set visible, false to set invisible
   * @see #setModelColumnVisible(int, boolean) 
   */
  public void setOriginalColumnVisible(int originalIndex, boolean visible) {
    // get the tablecolumn
    TableColumn column = originalColumns.get(originalIndex);   // will throw exception if out of bounds!
    // get the current visible index, -1 = not visible
    int visibleIndex = tableColumns.indexOf(column);
    if (visible)  {
      // make visible (again)
      if (visibleIndex == -1) { // only if not visible
        super.addColumn(column);
        int visibleSize = tableColumns.size();
        // move to proper position
        int orgIndex = 0;
        visibleIndex = 0;
        while (visibleIndex < visibleSize-1 &&
               orgIndex < originalIndex)  {
          column = originalColumns.get(originalIndex);
          if (tableColumns.indexOf(column) >= 0) {
            visibleIndex++;
          }
          orgIndex++;
        }
        // move to visibleIndex
        if (visibleIndex < visibleSize -1)  {
          super.moveColumn(visibleSize - 1, visibleIndex);
        }
      }
    }
    else  {
      // make invisible
      if (visibleIndex >= 0)  { // only if visible
        super.removeColumn(column);
      }
    }
  }
  
  
  
  /**
   * Same as setOriginalColumn but index corresponds to the data-model (model-index).
   * 
   * @param modelIndex the model column index
   * @param visible true to set visible, false to set invisible
   * @see #setOriginalColumnVisible(int, boolean) 
   */
  public void setModelColumnVisible(int modelIndex, boolean visible)  {
    TableColumn column = getColumnByModelIndex(modelIndex);
    if (column != null) {
      setOriginalColumnVisible(originalColumns.indexOf(column), visible);
    }
  }
  
  
  
  
  /**
   * Reorders columns according an array of ints.<br>
   * The index in the array is the column index, whereas the value is the
   * data-model index.
   * All columns in modelIndexes[] are visible afterwards!
   * Any columns in originalColumns not in modelIndexes[] will be removed!
   * 
   * @param modelIndexes the column indexes
   */
  public void reorderColumns(int[] modelIndexes)  {
    // remove all visible columns
    while (tableColumns.size() > 0) {
      super.removeColumn(tableColumns.firstElement());
    }
    // build a new internal vector of columns in the order given by modelIndexes
    Vector<TableColumn> columns = new Vector<TableColumn>();
    for (int i=0; i < modelIndexes.length; i++) {
      columns.add(getColumnByModelIndex(modelIndexes[i]));
    }
    // this is our new originalColumn table
    originalColumns = columns;
    
    // make all columns in originalColumns visible
    for (int i=0; i < originalColumns.size(); i++)  {
      setOriginalColumnVisible(i, true);
    }
  }
  
  
  /**
   * Gets all model indexes sorted according to the current view.<br>
   * The invisible columns come last.
   * @return the column indexes (model index)
   */
  public int[] getModelIndexes()  {
    int[] indexes = new int[originalColumns.size()];
    int i=0;
    for (TableColumn column: tableColumns)  {
      indexes[i++] = column.getModelIndex();
    }
    for (TableColumn column: originalColumns)  {
      if (!tableColumns.contains(column)) {
        indexes[i++] = column.getModelIndex();
      }
    }
    return indexes;
  }
  
}
