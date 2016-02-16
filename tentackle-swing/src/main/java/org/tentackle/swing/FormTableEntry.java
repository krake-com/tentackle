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


package org.tentackle.swing;

import java.awt.Rectangle;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.tentackle.common.Compare;
import org.tentackle.locale.I18nTranslation;
import org.tentackle.swing.BindableTableModel.BindType;
import org.tentackle.swing.bind.FormTableBinding;




/**
 * Entry per object that describes the data and configuration in order to
 * keep all table-config-stuff in one place.
 *
 * @param <T> the class of the object decorated by this FormTableEntry
 */
abstract public class FormTableEntry<T> implements FormTableRowObject {

  private FormTableModel<T> model;    // the model this entry lives in
  private int row;                    // the row of this entry with respect to the model


  // --------------- methods referring to all objects in a table ------------

  /**
   * Creates a new instance of an entry for a given data-object.
   *
   * @param object the data-object
   * @return the FormTableEntry for this object
   */
  abstract public FormTableEntry<T> newInstance (T object);

  /**
   * Determines the number of data-model columns.
   * Notice that not all columns may actually be visible.
   *
   * @return the number of columns
   */
  abstract public int getColumnCount();

  /**
   * Describes the column name.<br>
   * This may be a symbolic name if getDisplayedColumnName()
   * is overridden.
   *
   * @param mColumn the datamodel-column
   * @return the name of the column
   */
  abstract public String getColumnName(int mColumn);

  /**
   * Gets the displayed column name.<br>
   * By default the column-name and the displayed column name
   * are the same.
   * @param mColumn the datamodel column
   * @return the display name of the column
   */
  public String getDisplayedColumnName(int mColumn) {
    I18nTranslation i18nTranslation = getI18nTranslation(mColumn);
    if (i18nTranslation != null) {
      return i18nTranslation.getText();
    }
    else  {
      return getColumnName(mColumn);
    }
  }

  /**
   * Gets the translation for given column.
   *
   * @param mColumn the datamodel column
   * @return the translation
   */
  public I18nTranslation getI18nTranslation(int mColumn) {
    // @todo: this should be a generic lookup to the I18N backend!
    return null;
  }


  /**
   * Determines the class for a given column.<br>
   * If not overridden (or returning null) the class will be
   * determined by inspecting the data.
   *
   * @param mColumn the datamodel-column
   * @return the column class or null
   */
  public Class<?> getColumnClass (int mColumn) {
    return null;
  }


  /**
   * Defines the format (for numeric or date/time-types)
   * for each column. If null is returned a default
   * format will be used according to the column class.
   *
   * @param mColumn the datamodel-column
   * @return the format or null if default
   */
  public String getFormat(int mColumn)  {
    return null;
  }

  /**
   * Defines the horizontal alignment
   * for each column.
   *
   * @param mColumn the datamodel-column
   * @return the alignment or null if default
   */
  public Integer getHorizontalAlignment(int mColumn)  {
    return null;
  }

  /**
   * Defines the vertical alignment
   * for each column.
   *
   * @param mColumn the datamodel-column
   * @return the alignment or null if default
   */
  public Integer getVerticalAlignment(int mColumn)  {
    return null;
  }

  /**
   * Defines the "blankzero" attribute
   * for each column.
   *
   * @param mColumn the datamodel-column
   * @return true if blank zeros, false if not, null if use default from editor
   */
  public Boolean isBlankZero(int mColumn)  {
    return null;
  }


  /**
   * Gets the binding for a given model column.
   *
   * @param mColumn the model column
   * @return the binding, null if none
   */
  public FormTableBinding getBinding(int mColumn) {
    return model == null ? null : model.getBinding(mColumn);
  }


  /**
   * Gets the autoselect flag.
   *
   * @param mColumn the datamodel-column
   * @return true if autoselect, false if not, null if use default from editor
   */
  public Boolean isAutoSelect(int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      return binding.isAutoSelect();
    }
    return null;
  }


  /**
   * Gets the maximum columns for text cell editors.
   *
   * @param mColumn the model column index
   * @return the max columns, null if not defined
   */
  public Integer getMaxColumns(int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      return binding.getMaxColumns();
    }
    return null;
  }


  /**
   * Gets the scale for fractional numeric cell editors.
   *
   * @param mColumn the model column index
   * @return the max columns, null if not defined
   */
  public Integer getScale(int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      return binding.getScale();
    }
    return null;
  }


  /**
   * Defines the character conversion attribute
   * for each column. Default is null.
   *
   * @param mColumn the datamodel-column
   * @return the conversion, null if use default from editor
   */
  public Character getConvert (int mColumn)  {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      return binding.getConvert();
    }
    return null;
  }


  /**
   * Defines the adjust attribute
   * for each column. Default is null.
   *
   * @param mColumn the datamodel-column
   * @return the adjust-flag, null if use default from editor
   */
  public Character getAdjust (int mColumn)  {
    return null;
  }




  /**
   * Determines whether the given column is summable.<br>
   * The SumFormTableEntry will sumup all numeric columns by default.
   * However, for some numeric columns it doesn't make sense to build sums.
   * In this case, better override this method.
   * Furthermore, some apps check the first
   * row to determine whether a sumup-Button should be visible at all.
   * If the first row contains null-values, it cannot eliminate the possibilty
   * that this column is a numeric unless the getColumnClass() method explicitly
   * tells so.
   *
   * @param mColumn the datamodel column
   * @return true if column is definitely NOT numeric, else we dont know
   */
  public boolean isColumnNotSummable(int mColumn)  {
    Class<?> clazz = getColumnClass(mColumn);
    if (clazz != null && !Number.class.isAssignableFrom(clazz)) {
      return true;    // is not a number
    }
    Object value = getValueAt(mColumn);
    return value != null && !(value instanceof Number);
  }




  /**
   * Determines whether the cell renderers are fixed.<br>
   * FormTable invokes getCellRenderer in FormTableEntry only once to
   * improve performance. If the cellrenderer changes according to
   * content this method must be overridden.
   *
   * @return false if FormTable should invoke getCellRenderer for every cell.
   */
  public boolean isCellRendererFixed()  { return true; }

  /**
   * Determines whether the cell editors are fixed.<br>
   * FormTable invokes getCellEditor in FormTableEntry only once to
   * improve performance. If the celleditor changes according to
   * content this method must be overridden.
   *
   * @return false if FormTable should invoke getCellEditor for every cell.
   */
  public boolean isCellEditorFixed()  { return true; }

  /**
   * Determines whether the cell rectangles are fixed.<br>
   * Usually the cell dimension is fixed and does not depend on the data.
   * However, in special cases (e.g. multi-column cells), it is desirable
   * to get the cellRect computed for every cell.
   *
   * @return true if cell-dimensions are fixed (default).
   */
  public boolean isCellRectFixed() { return true; }





  // --------------- methods referring to a single row ----------------------

  /**
   * Sets the model this entry lives in.
   * Will be invoked in FormTableModel.getEntryAt.
   *
   * @param model the data-model
   */
  public void setModel(FormTableModel<T> model)  {
    this.model = model;
  }

  /**
   * Gets the model.
   * Useful to determine the chain of models up to the table
   * @return the data-model the entry lives in
   */
  @Override
  public FormTableModel<T> getModel()  {
    return model;
  }


  @Override
  public FormTable getTable() {
    return model == null ? null : model.getTable();
  }


  /**
   * Sets the row of this entry with respect to the model.
   * Will be invoked in FormTableModel.getEntryAt.
   *
   * @param row the data-model row
   */
  public void setRow(int row)  {
    this.row = row;
  }

  /**
   * Gets the row of this entry with respect to the model.
   * Useful to determine the model row and via the
   * model the view-row.
   * @return the data-model row of this entry
   */
  @Override
  public int getRow()  {
    return row;
  }

  /**
   * Gets the object wrapped by this entry.
   *
   * @return the data object
   */
  abstract public T getObject();


  /**
   * Gets the column-object for this entry in a given column.<br>
   * The default implementation tries to use the binding, if any.
   *
   * @param mColumn the datamodel-column
   * @return the column data object
   */
  @Override
  public Object getValueAt (int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      binding.setBoundRootObject(this);
      Object modelValue = binding.getModelValue();
      binding.setViewValue(modelValue); // this determines the mandatory/changeable attribute!
      return modelValue;
    }
    else  {
      return null;    // must be implemented in derived class
    }
  }


  /**
   * Sets the data object for a column.<br>
   * The default implementation tries to use the binding, if any.
   *
   * @param mColumn the datamodel-column
   * @param value the cell value
   */
  @Override
  public void setValueAt (int mColumn, Object value) {
    FormTableBinding binding = getBinding(mColumn);
    if (binding != null) {
      binding.setBoundRootObject(this);
      binding.setModelValue(value);
      binding.validate();
    }
    // else: must be implemented in derived class
  }


  /**
   * Determines whether the model using this entry is bindable and if so, which kind of binding to use.
   * <br>
   * The default implementation returns {@link BindableTableModel.BindType#BIND}.
   *
   * @return the binding type
   */
  public BindType getBindType() {
    return BindType.BIND;
  }

  /**
   * Gets the binding path for given model column.<br>
   * The returned path is relative to the object described by this table entry.
   *
   * @param mColumn the datamodel-column
   * @return the binding path, null if not bound
   */
  public String getBindingPath(int mColumn) {
    return null;    // not bound by default
  }



  /**
   * Performs an update of the current entry to the underlying
   * database cursor, if the data-model is based on a cursor.
   * The default implemenation does nothing and returns true.
   *
   * @param mRow the datamodel-row
   * @return true if update sucessful, false if not.
   */
  public boolean updateCursor(int mRow) { return true; }

  /**
   * Performs an update of the current entry to the underlying
   * list, if the data-model is based on a List.
   * The default implemenation does nothing and returns true.
   *
   * @param mRow the datamodel-row
   * @return true if update sucessful, false if not.
   */
  public boolean updateList(int mRow) { return true; }

  /**
   * Performs an update of the current entry to the underlying
   * array, if the data-model is based on an object array.
   * The default implemenation does nothing and returns true.
   *
   * @param mRow the datamodel-row
   * @return true if update sucessful, false if not.
   */
  public boolean updateArray(int mRow) { return true; }


  /**
   * Compares this entry with another one (for sorting).
   * If the data-objects implement the Comparable-interface the compareTo
   * method of the data-objects will be used.
   * Otherwise the string-represenation (from toString()) will be compared.
   * This default behaviour is ok for most applications.
   *
   * @param entry to be compared against this entry
   * @param compareBy is an array of mColumns where 0 is the first and
   *        negative minus 1 means descending. I.e.: [-1, 2] means:
   *        first column descending, third ascending
   *
   * @return a positive integer if this entry is logically "larger" than the
   *         given entry. A negative if "smaller" and zero if "equal".
   */

  @SuppressWarnings("unchecked")
  public int compareTo (FormTableEntry<? extends T> entry, int[] compareBy)  {
    int rv = 0;
    for (int i=0; rv == 0 && i < compareBy.length; i++) {
      int col = compareBy[i];            // index for column
      boolean descending = false;        // false = ascending
      if (col < 0)  {
        col = -col - 1;
        descending = true;
      }
      try {
        // try Comparable first (works in most cases)
        rv = Compare.compare((Comparable<Object>)getValueAt(col), (Comparable<Object>)entry.getValueAt(col));
      }
      catch (Exception e) {
        Object o1 = getValueAt(col);
        Object o2 = entry.getValueAt(col);
        String s1 = o1 != null ? o1.toString() : null;
        String s2 = o2 != null ? o2.toString() : null;
        rv = Compare.compare(s1, s2);
      }
      if (descending) {
        rv = -rv;
      }
    }
    return rv;
  }



  /**
   * Compares what the user sees on the GUI between two HistoryTableEntries.
   * This is in order to suppress invisible changes (i.e. editedBy, editedSince)
   *
   * @param entry to be compared against this entry
   * @return true if equal.
   */
  public boolean isVisiblyEqual(FormTableEntry<? extends T> entry)  {
    for (int col = getColumnCount() - 1; col >= 0; --col) {
      Object o1 = getValueAt(col);
      Object o2 = entry.getValueAt(col);
      String s1 = o1 != null ? o1.toString() : null;
      String s2 = o2 != null ? o2.toString() : null;
      if (Compare.compare(s1, s2) != 0) {
        return false;
      }
    }
    return true;
  }


  /**
   * Gets the cell renderer for a given column.<br>
   * Depending on isCellRendererFixed() this method is invoked
   * only once per column or for each cell.
   * The default implementation returns null, i.e. a
   * default renderer depending on the class is used.
   *
   * @param mColumn the datamodel-column
   * @return the renderer or null if default
   */
  public TableCellRenderer getCellRenderer(int mColumn) { return null; }


  /**
   * Determines whether the binding allows given cell to be changeable.
   * <p>
   * Note: can be used to implement {@link #isCellEditable(int)} for
   * interactive table entries.
   *
   * @param mColumn the model column
   * @return true if changeable, false if no binding or not changeable
   */
  public boolean isBindingChangeable(int mColumn) {
    FormTableBinding binding = getBinding(mColumn);
    return binding != null && binding.isChangeable();
  }

  /**
   * Determines whether the cell is editable or not.<br>
   * The default is not editable.
   *
   * @param mColumn the datamodel-column
   * @return true if the cell is editable
   * @see #isBindingChangeable(int)
   */
  public boolean isCellEditable(int mColumn) { return false; }


  /**
   * Gets the cell editor for a given column.
   * Depending on isCellEditorFixed() this method is invoked
   * only once per column or for each cell.
   * Furthermore the cell must be editable.
   * The default implementation returns null, i.e. a
   * default editor depending on the class is used.
   *
   * @param mColumn the datamodel-column
   * @return the editor or null if default
   */
  public TableCellEditor getCellEditor(int mColumn) { return null; }



  // ------------- methods that apply to the table and optionally to the entry ----------


  /**
   * Gets the cellrect for a column.<br>
   * The method is only invoked if isCellRectFixed() returns false.
   * <p>
   * Note:
   * Usually tables with dynamic cell sizes (i.e. multispan columns)
   * don't allow the user to change column ordering and/or sort rows.
   * For optimization reasons the given row and column are according
   * to the view which is usually is identical to the model.
   * If this is not the case (which is hard to handle btw.) getCellRect
   * must convert the row and column to the datamodel.
   *
   * @param vRow the row in table view
   * @param vColumn the column in table view
   * @param includeSpacing is true to include margins
   * @return the cellrect for a cell, null if use default
   */
  public Rectangle getCellRect(int vRow, int vColumn, boolean includeSpacing) { return null; }

  /**
   * Determines whether the cell is visible or not.<br>
   * The method is only invoked if isCellRectFixed() returns false.
   * Invisible cells are not rendered.
   * Overwriting this method usually makes sense
   * if cells are merged depending on the data, i.e. the
   * merged cells should not be rendered.
   *
   * The default is visible.
   *
   * @param vRow the row in table view
   * @param vColumn the column in table view
   * @return true if the cell is visible.
   */
  public boolean isCellVisible(int vRow, int vColumn) { return true; }

  /**
   * Gets the referenced row if cells are merged.<br>
   * If cells are merged they must reference to valid cell.
   * The method is only invoked if isCellRectFixed() returns false.
   *
   * @param vRow the row in table view
   * @param vColumn the column in table view
   * @return the referenced row for this cell
   */
  public int getReferencedRow(int vRow, int vColumn) { return vRow; }

  /**
   * Gets the referenced column if cells are merged.<br>
   * If cells are merged they must reference to valid cell.
   * The method is only invoked if isCellRectFixed() returns false.
   *
   * @param vRow the row in table view
   * @param vColumn the column in table view
   * @return the referenced column for this cell
   */
  public int getReferencedColumn(int vRow, int vColumn) { return vColumn; }

  /**
   * Determines whether the horizontal grid line following the
   * given row should be drawn or not.
   * The method is only invoked if isCellRectFixed() returns false.
   *
   * @param vRow the row in table view
   * @return true if draw horizontal grid
   */
  public boolean getShowHorizontalLine(int vRow) { return true; }

  /**
   * Determines whether the vertical grid line following the
   * given cell should be drawn or not.
   * The method is only invoked if isCellRectFixed() returns false.
   *
   * @param vRow the row in table view
   * @param vColumn the column in table view
   * @return true if draw horizontal grid
   */
  public boolean getShowVerticalLine(int vRow, int vColumn) { return true; }



  // ------------------------------- utility methods --------------------------------

  /**
   * Fires an update of all cells in the current row.
   */
  public void fireRowUpdated() {
    getModel().fireTableRowsUpdated(row, row);
  }

  /**
   * Fires an update of the given cells in current row.
   *
   * @param mColumns columns with respect to the model
   */
  public void fireCellsUpdated(int... mColumns) {
    for (int col: mColumns) {
      getModel().fireTableCellUpdated(row, col);
    }
  }

}
