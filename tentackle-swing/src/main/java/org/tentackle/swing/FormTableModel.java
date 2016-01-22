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

import java.util.List;
import org.tentackle.swing.bind.FormTableBinder;
import org.tentackle.swing.bind.FormTableBinding;


/**
 * Table-model for {@link FormTable}s.
 *
 * @param <T> the type managed by the model
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableModel<T> extends AbstractFormTableModel<T> implements BindableTableModel<T> {

  private FormTableEntry<T> template;             // template zum Erzeugen von Objekten
  private FormTableEntry<T>[] entries;            // alle Objekte im Cache
  private FormTableBinder binder;                 // the binder, if any

  private List<T> list;                           // != null falls über List
  private T[] array;                              // != null falls über Array


  /**
   * Creates a table model for a list of objects.
   *
   * @param template  the table entry as a template to create other entries
   * @param list      the list of objects
   */
  public FormTableModel (FormTableEntry<T> template, List<T> list) {
    setTemplate(template);
    listChanged(list);
  }

  /**
   * Creates a table model for an array of objects.
   *
   * @param template  the table entry as a template to create other entries
   * @param array     the array of objects
   */
  public FormTableModel (FormTableEntry<T> template, T[] array) {
    setTemplate(template);
    listChanged(array);
  }


  /**
   * Creates an empty table model for a given template.
   *
   * @param template  the table entry as a template to create other entries
   */
  public FormTableModel (FormTableEntry<T> template) {
    setTemplate(template);
  }


  /**
   * Sets the template.<br>
   * Useful if context has changed.
   *
   * @param template the new template
   */
  public void setTemplate(FormTableEntry<T> template)  {
    if (template == null) {
      throw new IllegalArgumentException("template must not be null");
    }
    this.template = template;
    template.setModel(this);
  }


  /**
   * Creates a binder for this form.<br>
   * The default implementation invokes
   * {@code FormBindingFactory.createFormTableBinder(this)}.
   *
   * @return the binder
   */
  protected FormTableBinder createBinder() {
    return FormUtilities.getInstance().getBindingFactory().createFormTableBinder(this);
  }


  @Override
  public FormTableBinder getBinder() {
    if (binder == null) {
      binder = createBinder();
    }
    return binder;
  }


  @Override
  public BindType getBindType() {
    return template.getBindType();
  }

  @Override
  public Class<?> getBindingRootClass() {
    return template.getClass();
  }


  /**
   * Binds the model.
   * <br>
   * The method simply binds this model according to its {@link BindableTableModel.BindType}.
   * If the bindtype is {@link BindableTableModel.BindType#NONE}, nothing will be bound.
   *
   * @see #getBindType()
   * @see #getBinder()
   */
  public void bind() {
    BindType bindType = getBindType();
    if (bindType == BindType.BIND) {
      getBinder().bind();
    }
    else if (bindType == BindType.BINDALL) {
      getBinder().bindAllInherited();
    }
  }


  /**
   * Prints the classname of the table entry.
   *
   * @return the class name of the template
   */
  @Override
  public String toString() {
    return template.getClass().getName();
  }



  @Override
  public FormTableEntry<T> getTemplate() {
    return template;
  }


  @Override
  public int getRowCount() {
    return entries == null ? 0 : entries.length;
  }


  @Override
  public int getColumnCount() {
    return template.getColumnCount();
  }


  @Override
  public String getColumnName (int columnIndex) {
    return template.getColumnName(columnIndex);
  }

  @Override
  public String getDisplayedColumnName (int columnIndex) {
    return template.getDisplayedColumnName(columnIndex);
  }


  /**
   * Gets the binding for a given column.
   *
   * @param columnIndex the model column
   * @return the binding, null if column not bound
   */
  @Override
  public FormTableBinding getBinding(int columnIndex) {
    return binder == null ? null : binder.getBinding(columnIndex);
  }


  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    FormTableEntry<T> entry = getEntryAt(rowIndex);
    if (entry == null) {
      return null;
    }
    else  {
      return entry.getValueAt(columnIndex);
    }
  }


  /**
   * Sets the cell value.
   */
  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    FormTableEntry<T> entry = getEntryAt(rowIndex);
    if (entry != null)  {
      entry.setValueAt(columnIndex, aValue);
      setDataChanged(true);
      fireTableCellUpdated(rowIndex, columnIndex);
    }
  }


  /**
   * Gets the binding path for given model column.<br>
   * The returned path is relative to the object described by this table entry.
   *
   * @param mColumn the datamodel-column
   * @return the binding path, null if not bound
   */
  @Override
  public String getBindingPath(int mColumn) {
    return template.getBindingPath(mColumn);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to propagate triggerValueChanged()
   */
  @Override
  public void setDataChanged(boolean dataChanged) {
    boolean wasChanged = isDataChanged();
    super.setDataChanged(dataChanged);
    if (!wasChanged && dataChanged) {
      FormTable<T> table = getTable();
      if (table != null)  {
        FormUtilities.getInstance().triggerValueChanged(table);
      }
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to get the table if models are chained
   */
  @Override
  public FormTable<T> getTable()  {
    AbstractFormTableModel<T> model = this;
    FormTable<T> table = super.getTable();
    while (table == null)  {
      model = model.getMap();
      if (model == null) {
        break;
      }
      table = model.getTable();
    }
    return table;
  }


  @Override
  @SuppressWarnings("unchecked")
  public FormTableEntry<T> getEntryAt (int row)  {

    synchronized (this) {
      if (row < 0 || row >= getRowCount())  {
        return null;
      }
      else  {
        if (entries[row] == null) {
          // build new entry
          FormTableEntry<T> entry = null;
          if (list != null)  {
            entry = template.newInstance(list.get(row));
          }
          else if (array != null) {
            entry = template.newInstance(array[row]);
          }
          if (entry != null)  {
            entry.setModel(this);
            entry.setRow(row);
            entries[row] = entry;
          }
        }
        return entries[row];
      }
    }
  }


  @Override
  public boolean setEntryAt (FormTableEntry<T> entry, int row) {
    boolean rv = false;
    synchronized (this) {
      if (row >= 0 && row < getRowCount())  {
        entries[row] = entry;
        // update im darunterliegenden Layer
        if      (list   != null) {
          rv = entry.updateList(row);         // falls List
        }
        else if (array != null) {
          rv = entry.updateArray(row);        // falls Array
        }
        if (rv) {
          setDataChanged(true);
          fireTableRowsUpdated(row, row);
        }
        return rv;
      }
    }
    return false;
  }


  @Override
  public Class<?> getColumnClass (int columnIndex) {
    // check if formtable entry overrides default
    Class<?> colClass = template.getColumnClass(columnIndex);
    if (colClass != null) {
      return colClass;
    }
    // analyze the data
    int rows = getRowCount();
    for (int i=0; i < rows; i++)  {
      FormTableEntry<T> entry = getEntryAt(i);
      if (entry != null)  {
        Object value = entry.getValueAt(columnIndex);
        if (value != null) {
          return value.getClass();
        }
      }
    }
    return Object.class;
  }

  @Override
  public boolean isCellEditable (int rowIndex, int columnIndex)  {
    FormTableEntry<T> entry = getEntryAt(rowIndex);
    return entry != null ? entry.isCellEditable(columnIndex) : false;
  }




  /**
   * Sets a new list of objects and fires tableDataChanged.
   *
   * @param list the new list of objects
   */
  @SuppressWarnings("unchecked")
  public void listChanged(List<T> list) {
    synchronized (this) {
      this.list   = list;
      this.array  = null;
      setDataChanged(false);
      entries = (list == null ? null : new FormTableEntry[list.size()]);
      fireTableDataChanged();   // generate the right fireTableChanged-Event
    }
  }



  /**
   * Sets a new array of objects and fires tableDataChanged.
   *
   * @param array the new array of objects
   */
  @SuppressWarnings("unchecked")
  public void listChanged(T[] array) {
    synchronized (this) {
      this.array  = array;
      this.list   = null;
      setDataChanged(false);
      entries = (array == null ? null : new FormTableEntry[array.length]);
      fireTableDataChanged();   // generate the right fireTableChanged-Event
    }
  }




  /**
   * Denotes that a range of rows have been changed and fires tableRowsUpdated.
   * The row numbers are silently aligned if out of range.
   *
   * @param firstRow the first row
   * @param lastRow the last changed row (&ge; firstRow)
   */
  public void listUpdated(int firstRow, int lastRow) {
    synchronized (this) {

      if (firstRow < 0) {
        firstRow = 0;
      }
      if (lastRow < firstRow) {
        lastRow = firstRow;
      }
      if (lastRow >= entries.length) {
        lastRow = entries.length - 1;
      }

      if (lastRow >= firstRow) {
        for (int i=firstRow; i <= lastRow; i++) {
          entries[i] = null;    // row will be reloaded if used
        }
        setDataChanged(true);
        fireTableRowsUpdated(firstRow, lastRow);
      }
    }
  }



  /**
   * Denotes that a table cell has changed and fires tableCellUpdated.
   * If the row is out of range, nothing will be done.
   * The handling of the column depends on the listener for the
   * TableModelEvent.
   *
   * @param rowIndex the row number
   * @param columnIndex the column index
   */
  public void listCellUpdated(int rowIndex, int columnIndex) {
    synchronized (this) {
      if (rowIndex >= 0 && rowIndex < entries.length) {
        entries[rowIndex] = null;
        setDataChanged(true);
        fireTableCellUpdated(rowIndex, columnIndex);
      }
    }
  }


  /**
   * Denotes that a range of rows have been insert and fires tableRowsInserted.
   * The row numbers are silently aligned if out of range.
   * Will nullpex if not in list-mode.
   *
   * @param firstRow the first row
   * @param lastRow the last changed row (&ge;firstRow)
   */
  @SuppressWarnings("unchecked")
  public void listInserted(int firstRow, int lastRow)  {
    synchronized (this) {
      int newSize = list.size();
      if (firstRow < 0) {
        firstRow = 0;
      }
      if (lastRow < firstRow) {
        lastRow = firstRow;
      }
      if (lastRow >= newSize) {
        lastRow = newSize - 1;
      }
      if (lastRow >= firstRow) {
        FormTableEntry<T>[] oldEntries = entries;
        entries = new FormTableEntry[newSize];
        int i;
        for (i=0; i < firstRow; i++) {
          entries[i] = oldEntries[i];
        }
        for (; i <= lastRow; i++) {
          entries[i] = null;
        }
        int inserted = lastRow - firstRow + 1;
        for (; i < entries.length; i++) {
          entries[i] = oldEntries[i - inserted];
        }
        setDataChanged(true);
        fireTableRowsInserted(firstRow, lastRow);
      }
    }
  }


  /**
   * Denotes that a range of rows have been deleted and fires tableRowsDeleted.
   * The row numbers are silently aligned if out of range.
   * Will nullpex if not in list-mode.
   *
   * @param firstRow the first row
   * @param lastRow the last changed row (&ge; firstRow)
   */
  @SuppressWarnings("unchecked")
  public void listDeleted(int firstRow, int lastRow) {
    synchronized (this) {
      if (firstRow < 0) {
        firstRow = 0;
      }
      if (lastRow < firstRow) {
        lastRow = firstRow;
      }
      if (lastRow >= entries.length) {
        lastRow = entries.length - 1;
      }
      if (lastRow >= firstRow) {
        FormTableEntry<T>[] oldEntries = entries;
        entries = new FormTableEntry[list.size()];
        int i;
        for (i=0; i < firstRow; i++)  {
          entries[i] = oldEntries[i];
        }
        int deleted = lastRow - firstRow + 1;
        for (; i < entries.length; i++) {
          entries[i] = oldEntries[i + deleted];
        }
        setDataChanged(true);
        fireTableRowsDeleted(firstRow, lastRow);
      }
    }
  }

}
